package com.example.service;

import com.example.config.ImportConfigProperties;
import com.example.dto.LegacyClient;
import com.example.dto.LegacyNote;
import com.example.entity.CompanyUser;
import com.example.entity.PatientNote;
import com.example.entity.PatientProfile;
import com.example.model.ImportStatistics;
import com.example.monitoring.ImportMetrics;
import com.example.repository.CompanyUserRepository;
import com.example.repository.PatientNoteRepository;
import com.example.repository.PatientProfileRepository;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ParallelImportService {

    private final RetryableApiService apiService;
    private final PatientProfileRepository patientRepository;
    private final CompanyUserRepository userRepository;
    private final PatientNoteRepository noteRepository;
    private final ImportConfigProperties config;
    private final ImportMetrics metrics;
    private final ExecutorService executorService;

    public ParallelImportService(RetryableApiService apiService,
                                 PatientProfileRepository patientRepository,
                                 CompanyUserRepository userRepository,
                                 PatientNoteRepository noteRepository,
                                 ImportConfigProperties config,
                                 ImportMetrics metrics) {
        this.apiService = apiService;
        this.patientRepository = patientRepository;
        this.userRepository = userRepository;
        this.noteRepository = noteRepository;
        this.config = config;
        this.metrics = metrics;

        this.executorService = Executors.newFixedThreadPool(
                config.getParallelThreads(),
                r -> {
                    Thread t = new Thread(r, "hospital-import-" + Thread.currentThread().getId());
                    t.setDaemon(true);
                    return t;
                }
        );

        log.info("ParallelImportService инициализирован с {} потоками", config.getParallelThreads());
    }

    @PreDestroy
    public void cleanup() {
        log.info("Закрытие пула потоков ParallelImportService...");

        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                log.warn("Пул потоков не завершился за 60 секунд, принудительное закрытие...");
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public ImportStatistics performParallelImport() {
        ImportStatistics stats = new ImportStatistics();
        stats.setStartTime(LocalDateTime.now());

        try {
            log.info("Начинаем параллельный импорт заметок (потоков: {})", config.getParallelThreads());

            List<PatientProfile> activePatients = patientRepository.findActivePatients();
            log.info("Найдено {} активных пациентов", activePatients.size());

            if (activePatients.isEmpty()) {
                stats.setEndTime(LocalDateTime.now());
                return stats;
            }

            List<LegacyClient> legacyClients = apiService.getAllClientsWithRetry();
            Map<String, LegacyClient> clientMap = legacyClients.stream()
                    .collect(Collectors.toMap(LegacyClient::getGuid, Function.identity()));

            List<List<PatientProfile>> patientBatches = partitionList(activePatients, config.getPatientBatchSize());

            List<CompletableFuture<ImportStatistics>> futures = patientBatches.stream()
                    .map(batch -> CompletableFuture.supplyAsync(() ->
                            processPatientsSpan(batch, clientMap), executorService))
                    .collect(Collectors.toList());

            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                    futures.toArray(new CompletableFuture[0]));

            try {
                allFutures.get(config.getOperationTimeoutMinutes(), TimeUnit.MINUTES);
            } catch (TimeoutException e) {
                log.error("Превышен таймаут операции импорта");
                stats.setHasCriticalError(true);
                return stats;
            }

            for (CompletableFuture<ImportStatistics> future : futures) {
                try {
                    ImportStatistics batchStats = future.get();
                    mergeStatistics(stats, batchStats);
                } catch (Exception e) {
                    log.error("Ошибка получения результатов батча", e);
                    stats.incrementErrorCount();
                }
            }

            stats.setEndTime(LocalDateTime.now());
            log.info("Параллельный импорт завершен: {}", stats);

        } catch (Exception e) {
            log.error("Критическая ошибка при параллельном импорте", e);
            stats.setEndTime(LocalDateTime.now());
            stats.setHasCriticalError(true);
        }

        return stats;
    }

    private ImportStatistics processPatientsSpan(List<PatientProfile> patients,
                                                 Map<String, LegacyClient> clientMap) {
        ImportStatistics batchStats = new ImportStatistics();
        batchStats.setStartTime(LocalDateTime.now());

        for (PatientProfile patient : patients) {
            try {
                List<String> oldGuids = patient.getOldClientGuids();
                for (String oldGuid : oldGuids) {
                    LegacyClient legacyClient = clientMap.get(oldGuid);
                    if (legacyClient != null) {
                        importNotesForPatient(patient, legacyClient, batchStats);
                    }
                }
            } catch (Exception e) {
                log.error("Ошибка импорта для пациента {}", patient.getId(), e);
                batchStats.incrementErrorCount();
            }
        }

        batchStats.setEndTime(LocalDateTime.now());
        return batchStats;
    }

    private void importNotesForPatient(PatientProfile patient, LegacyClient legacyClient,
                                       ImportStatistics stats) throws Exception {
        LocalDate dateTo = LocalDate.now();
        LocalDate dateFrom = dateTo.minusDays(config.getDaysToImport());

        List<LegacyNote> legacyNotes = apiService.getClientNotesWithRetry(
                legacyClient.getAgency(),
                legacyClient.getGuid(),
                dateFrom,
                dateTo
        );

        for (LegacyNote legacyNote : legacyNotes) {
            try {
                importSingleNote(patient, legacyNote, stats);
            } catch (Exception e) {
                log.error("Ошибка импорта заметки {} для пациента {}",
                        legacyNote.getGuid(), patient.getId(), e);
                stats.incrementErrorCount();
            }
        }
    }

    @Transactional
    private void importSingleNote(PatientProfile patient, LegacyNote legacyNote,
                                  ImportStatistics stats) {
        if (!isValidNote(legacyNote)) {
            stats.incrementSkippedCount();
            return;
        }

        Optional<PatientNote> existingNote = noteRepository.findByLegacyNoteGuid(legacyNote.getGuid());
        CompanyUser user = getOrCreateUser(legacyNote.getLoggedUser());

        LocalDateTime createdDateTime = parseDateTime(legacyNote.getCreatedDateTime());
        LocalDateTime modifiedDateTime = parseDateTime(legacyNote.getModifiedDateTime());

        if (existingNote.isPresent()) {
            PatientNote note = existingNote.get();
            if (modifiedDateTime.isAfter(note.getLastModifiedDateTime())) {
                note.setNote(legacyNote.getComments());
                note.setLastModifiedDateTime(modifiedDateTime);
                note.setLastModifiedByUser(user);
                noteRepository.save(note);
                stats.incrementUpdatedCount();
            } else {
                stats.incrementSkippedCount();
            }
        } else {
            PatientNote newNote = new PatientNote();
            newNote.setPatient(patient);
            newNote.setNote(legacyNote.getComments());
            newNote.setCreatedDateTime(createdDateTime);
            newNote.setLastModifiedDateTime(modifiedDateTime);
            newNote.setCreatedByUser(user);
            newNote.setLastModifiedByUser(user);
            newNote.setLegacyNoteGuid(legacyNote.getGuid());

            noteRepository.save(newNote);
            stats.incrementCreatedCount();
        }
    }

    private boolean isValidNote(LegacyNote legacyNote) {
        return legacyNote.getGuid() != null && !legacyNote.getGuid().trim().isEmpty() &&
                legacyNote.getComments() != null && !legacyNote.getComments().trim().isEmpty();
    }

    private CompanyUser getOrCreateUser(String login) {
        if (login == null || login.trim().isEmpty()) {
            login = "system";
        }

        String finalLogin = login;
        return userRepository.findByLogin(login.trim())
                .orElseGet(() -> {
                    log.info("Создаем нового пользователя: {}", finalLogin);
                    return userRepository.save(new CompanyUser(finalLogin.trim()));
                });
    }

    private LocalDateTime parseDateTime(String dateTimeString) {
        if (dateTimeString == null || dateTimeString.trim().isEmpty()) {
            return LocalDateTime.now();
        }

        try {
            String cleanDateTime = dateTimeString.trim().replaceAll(" [A-Z]{3}$", "");
            return LocalDateTime.parse(cleanDateTime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception e) {
            log.warn("Не удалось распарсить дату: '{}', используем текущее время", dateTimeString);
            return LocalDateTime.now();
        }
    }

    private <T> List<List<T>> partitionList(List<T> list, int batchSize) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += batchSize) {
            partitions.add(list.subList(i, Math.min(i + batchSize, list.size())));
        }
        return partitions;
    }

    private void mergeStatistics(ImportStatistics target, ImportStatistics source) {
        target.setCreatedCount(target.getCreatedCount() + source.getCreatedCount());
        target.setUpdatedCount(target.getUpdatedCount() + source.getUpdatedCount());
        target.setSkippedCount(target.getSkippedCount() + source.getSkippedCount());
        target.setErrorCount(target.getErrorCount() + source.getErrorCount());

        if (source.isHasCriticalError()) {
            target.setHasCriticalError(true);
        }
    }
}