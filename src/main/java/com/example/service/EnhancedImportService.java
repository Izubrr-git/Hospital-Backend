package com.example.service;

import com.example.config.ImportConfigProperties;
import com.example.dto.LegacyClient;
import com.example.dto.LegacyNote;
import com.example.model.ImportStatistics;
import com.example.entity.CompanyUser;
import com.example.entity.PatientNote;
import com.example.entity.PatientProfile;
import com.example.monitoring.ImportMetrics;
import com.example.repository.CompanyUserRepository;
import com.example.repository.PatientNoteRepository;
import com.example.repository.PatientProfileRepository;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
@Slf4j
public class EnhancedImportService {

    private final LegacyApiService legacyApiService;
    private final PatientProfileRepository patientRepository;
    private final CompanyUserRepository userRepository;
    private final PatientNoteRepository noteRepository;
    private final ImportConfigProperties config;
    private final ImportMetrics metrics;

    private final DateTimeFormatter legacyDateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[ zzz]");

    public EnhancedImportService(LegacyApiService legacyApiService,
                                 PatientProfileRepository patientRepository,
                                 CompanyUserRepository userRepository,
                                 PatientNoteRepository noteRepository,
                                 ImportConfigProperties config,
                                 ImportMetrics metrics) {
        this.legacyApiService = legacyApiService;
        this.patientRepository = patientRepository;
        this.userRepository = userRepository;
        this.noteRepository = noteRepository;
        this.config = config;
        this.metrics = metrics;
    }

    public ImportStatistics performImport() {
        ImportStatistics stats = new ImportStatistics();
        stats.setStartTime(LocalDateTime.now());
        metrics.recordImportStarted();

        try {
            log.info("Начинаем импорт заметок из старой системы");

            // Получаем всех активных пациентов
            List<PatientProfile> activePatients = patientRepository.findActivePatients();
            log.info("Найдено {} активных пациентов", activePatients.size());

            if (activePatients.isEmpty()) {
                log.warn("Нет активных пациентов для импорта");
                stats.setEndTime(LocalDateTime.now());
                return stats;
            }

            // Получаем всех клиентов из старой системы
            List<LegacyClient> legacyClients = legacyApiService.getAllClients();
            log.info("Получено {} клиентов из старой системы", legacyClients.size());

            // Создаем мапу для быстрого поиска клиентов по GUID
            Map<String, LegacyClient> clientMap = legacyClients.stream()
                    .collect(Collectors.toMap(LegacyClient::getGuid, Function.identity()));

            // Обрабатываем пациентов батчами
            List<List<PatientProfile>> patientBatches = partitionList(activePatients, config.getPatientBatchSize());

            for (List<PatientProfile> batch : patientBatches) {
                processPatientsSpan(batch, clientMap, stats);
            }

            stats.setEndTime(LocalDateTime.now());
            log.info("Импорт завершен. Статистика: {}", stats);

            // Записываем метрики
            metrics.recordImportCompleted(stats.getDuration(), !stats.isHasCriticalError());
            metrics.recordNotesProcessed(stats.getCreatedCount(), stats.getUpdatedCount(), stats.getSkippedCount());

        } catch (Exception e) {
            log.error("Критическая ошибка при импорте", e);
            stats.setEndTime(LocalDateTime.now());
            stats.setHasCriticalError(true);
            metrics.recordImportError("critical_error");
        }

        return stats;
    }

    private void processPatientsSpan(List<PatientProfile> patients,
                                     Map<String, LegacyClient> clientMap,
                                     ImportStatistics stats) {
        for (PatientProfile patient : patients) {
            try {
                List<String> oldGuids = patient.getOldClientGuids();
                if (oldGuids.isEmpty()) {
                    continue;
                }

                for (String oldGuid : oldGuids) {
                    LegacyClient legacyClient = clientMap.get(oldGuid);
                    if (legacyClient != null) {
                        importNotesForPatient(patient, legacyClient, stats);
                    }
                }
            } catch (Exception e) {
                log.error("Ошибка импорта заметок для пациента {}", patient.getId(), e);
                stats.incrementErrorCount();
                metrics.recordImportError("patient_processing_error");
            }
        }
    }

    private void importNotesForPatient(PatientProfile patient, LegacyClient legacyClient,
                                       ImportStatistics stats) throws Exception {
        // Получаем заметки за указанный период
        LocalDate dateTo = LocalDate.now();
        LocalDate dateFrom = dateTo.minusDays(config.getDaysToImport());

        List<LegacyNote> legacyNotes = legacyApiService.getClientNotes(
                legacyClient.getAgency(),
                legacyClient.getGuid(),
                dateFrom,
                dateTo
        );

        log.debug("Получено {} заметок для пациента {} (клиент {})",
                legacyNotes.size(), patient.getId(), legacyClient.getGuid());

        for (LegacyNote legacyNote : legacyNotes) {
            try {
                importSingleNote(patient, legacyNote, stats);
            } catch (Exception e) {
                log.error("Ошибка импорта заметки {} для пациента {}",
                        legacyNote.getGuid(), patient.getId(), e);
                stats.incrementErrorCount();
                metrics.recordImportError("note_processing_error");
            }
        }
    }

    @Transactional
    public void importSingleNote(PatientProfile patient, LegacyNote legacyNote,
                                 ImportStatistics stats) {
        // Валидация входных данных
        if (legacyNote.getGuid() == null || legacyNote.getGuid().trim().isEmpty()) {
            log.warn("Пропускаем заметку без GUID для пациента {}", patient.getId());
            stats.incrementSkippedCount();
            return;
        }

        if (legacyNote.getComments() == null || legacyNote.getComments().trim().isEmpty()) {
            log.warn("Пропускаем пустую заметку {} для пациента {}", legacyNote.getGuid(), patient.getId());
            stats.incrementSkippedCount();
            return;
        }

        // Ищем существующую заметку
        Optional<PatientNote> existingNote = noteRepository.findByLegacyNoteGuid(legacyNote.getGuid());

        // Получаем или создаем пользователя
        CompanyUser user = getOrCreateUser(legacyNote.getLoggedUser());

        // Парсим даты
        LocalDateTime createdDateTime = parseDateTime(legacyNote.getCreatedDateTime());
        LocalDateTime modifiedDateTime = parseDateTime(legacyNote.getModifiedDateTime());

        if (existingNote.isPresent()) {
            // Обновляем существующую заметку
            PatientNote note = existingNote.get();

            // Проверяем, какая версия новее
            if (modifiedDateTime.isAfter(note.getLastModifiedDateTime())) {
                note.setNote(legacyNote.getComments());
                note.setLastModifiedDateTime(modifiedDateTime);
                note.setLastModifiedByUser(user);
                noteRepository.save(note);
                stats.incrementUpdatedCount();
                log.debug("Обновлена заметка {} для пациента {}", legacyNote.getGuid(), patient.getId());
            } else {
                stats.incrementSkippedCount();
                log.debug("Заметка {} пропущена - версия в БД новее", legacyNote.getGuid());
            }
        } else {
            // Создаем новую заметку
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
            log.debug("Создана заметка {} для пациента {}", legacyNote.getGuid(), patient.getId());
        }
    }

    private CompanyUser getOrCreateUser(String login) {
        if (login == null || login.trim().isEmpty()) {
            log.warn("Получен пустой логин пользователя, используем системного пользователя");
            login = "system";
        }

        String finalLogin = login;
        return userRepository.findByLogin(login.trim())
                .orElseGet(() -> {
                    log.info("Создаем нового пользователя: {}", finalLogin);
                    CompanyUser newUser = new CompanyUser(finalLogin.trim());
                    return userRepository.save(newUser);
                });
    }

    private LocalDateTime parseDateTime(String dateTimeString) {
        if (dateTimeString == null || dateTimeString.trim().isEmpty()) {
            log.warn("Получена пустая дата, используем текущее время");
            return LocalDateTime.now();
        }

        try {
            // Убираем часовой пояс если есть (CDT, EST, etc.)
            String cleanDateTime = dateTimeString.trim().replaceAll(" [A-Z]{3}$", "");
            return LocalDateTime.parse(cleanDateTime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception e) {
            log.warn("Не удалось распарсить дату: '{}', используем текущее время. Ошибка: {}",
                    dateTimeString, e.getMessage());
            return LocalDateTime.now();
        }
    }

    private <T> List<List<T>> partitionList(List<T> list, int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Размер батча должен быть больше 0");
        }

        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += batchSize) {
            partitions.add(list.subList(i, Math.min(i + batchSize, list.size())));
        }
        return partitions;
    }

    // Дополнительные методы для статистики и управления

    public long getTotalNotesCount() {
        return noteRepository.count();
    }

    public long getImportedNotesCount() {
        return noteRepository.countImportedNotes();
    }

    public LocalDateTime getLastImportTime() {
        return noteRepository.findTopByLegacyNoteGuidIsNotNullOrderByLastModifiedDateTimeDesc()
                .map(PatientNote::getLastModifiedDateTime)
                .orElse(null);
    }

    @Transactional
    public ImportStatistics getImportStatistics() {
        ImportStatistics stats = new ImportStatistics();

        long totalNotes = getTotalNotesCount();
        long importedNotes = getImportedNotesCount();
        LocalDateTime lastImport = getLastImportTime();

        // Подсчитываем заметки за последние сутки
        LocalDateTime yesterDay = LocalDateTime.now().minusDays(1);
        long recentImports = noteRepository.countImportedSince(yesterDay);

        stats.setCreatedCount((int) recentImports);
        // Дополнительные поля можно установить здесь

        log.info("Статистика: всего заметок={}, импортированных={}, за последние сутки={}",
                totalNotes, importedNotes, recentImports);

        return stats;
    }

    // Метод для импорта конкретного пациента (для отладки)
    @Transactional
    public ImportStatistics importSpecificPatient(Long patientId) {
        ImportStatistics stats = new ImportStatistics();
        stats.setStartTime(LocalDateTime.now());

        try {
            Optional<PatientProfile> patientOpt = patientRepository.findById(patientId);
            if (patientOpt.isEmpty()) {
                throw new IllegalArgumentException("Пациент с ID " + patientId + " не найден");
            }

            PatientProfile patient = patientOpt.get();
            if (!patient.isActive()) {
                throw new IllegalArgumentException("Пациент с ID " + patientId + " неактивен (статус: " + patient.getStatusId() + ")");
            }

            List<LegacyClient> allClients = legacyApiService.getAllClients();
            Map<String, LegacyClient> clientMap = allClients.stream()
                    .collect(Collectors.toMap(LegacyClient::getGuid, Function.identity()));

            List<String> oldGuids = patient.getOldClientGuids();
            for (String oldGuid : oldGuids) {
                LegacyClient legacyClient = clientMap.get(oldGuid);
                if (legacyClient != null) {
                    importNotesForPatient(patient, legacyClient, stats);
                } else {
                    log.warn("Клиент с GUID {} не найден в старой системе", oldGuid);
                }
            }

            stats.setEndTime(LocalDateTime.now());
            log.info("Импорт для пациента {} завершен: {}", patientId, stats);

        } catch (Exception e) {
            log.error("Ошибка импорта для пациента {}", patientId, e);
            stats.setEndTime(LocalDateTime.now());
            stats.setHasCriticalError(true);
            stats.incrementErrorCount();
        }

        return stats;
    }

    // Метод очистки старых импортированных записей
    @Transactional
    public int cleanupOldImportedNotes(int daysToKeep) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysToKeep);

        // Можно добавить удаление старых записей если нужно
        // Пока что только логируем
        long oldNotesCount = noteRepository.countImportedSince(cutoffDate);
        log.info("Найдено {} импортированных заметок старше {} дней", oldNotesCount, daysToKeep);

        return (int) oldNotesCount;
    }
}