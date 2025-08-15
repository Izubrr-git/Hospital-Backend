package com.example.Services;

import com.example.DTO.LegacyClient;
import com.example.DTO.LegacyNote;
import com.example.ImportStatistics;
import com.example.JPA_Entities.CompanyUser;
import com.example.JPA_Entities.PatientNote;
import com.example.JPA_Entities.PatientProfile;
import com.example.Repositories.CompanyUserRepository;
import com.example.Repositories.PatientNoteRepository;
import com.example.Repositories.PatientProfileRepository;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
@Slf4j
public class ImportService {
    private final LegacyApiService legacyApiService;
    private final PatientProfileRepository patientRepository;
    private final CompanyUserRepository userRepository;
    private final PatientNoteRepository noteRepository;
    private final DateTimeFormatter legacyDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[ zzz]");

    public ImportService(LegacyApiService legacyApiService,
                         PatientProfileRepository patientRepository,
                         CompanyUserRepository userRepository,
                         PatientNoteRepository noteRepository) {
        this.legacyApiService = legacyApiService;
        this.patientRepository = patientRepository;
        this.userRepository = userRepository;
        this.noteRepository = noteRepository;
    }

    public ImportStatistics performImport() {
        ImportStatistics stats = new ImportStatistics();
        stats.setStartTime(LocalDateTime.now());

        try {
            log.info("Начинаем импорт заметок из старой системы");

            // Получаем всех активных пациентов
            List<PatientProfile> activePatients = patientRepository.findActivePatients();
            log.info("Найдено {} активных пациентов", activePatients.size());

            // Получаем всех клиентов из старой системы
            List<LegacyClient> legacyClients = legacyApiService.getAllClients();
            log.info("Получено {} клиентов из старой системы", legacyClients.size());

            // Создаем мапу для быстрого поиска клиентов по GUID
            Map<String, LegacyClient> clientMap = legacyClients.stream()
                    .collect(Collectors.toMap(LegacyClient::getGuid, Function.identity()));

            for (PatientProfile patient : activePatients) {
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
                }
            }

            stats.setEndTime(LocalDateTime.now());
            log.info("Импорт завершен. Статистика: {}", stats);

        } catch (Exception e) {
            log.error("Критическая ошибка при импорте", e);
            stats.setEndTime(LocalDateTime.now());
            stats.setHasCriticalError(true);
        }

        return stats;
    }

    private void importNotesForPatient(PatientProfile patient, LegacyClient legacyClient,
                                       ImportStatistics stats) throws Exception {
        // Получаем заметки за последние 30 дней
        LocalDate dateTo = LocalDate.now();
        LocalDate dateFrom = dateTo.minusDays(30);

        List<LegacyNote> legacyNotes = legacyApiService.getClientNotes(
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

    private void importSingleNote(PatientProfile patient, LegacyNote legacyNote,
                                  ImportStatistics stats) {
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
                log.debug("Обновлена заметка {}", legacyNote.getGuid());
            } else {
                stats.incrementSkippedCount();
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
            log.debug("Создана заметка {}", legacyNote.getGuid());
        }
    }

    private CompanyUser getOrCreateUser(String login) {
        return userRepository.findByLogin(login)
                .orElseGet(() -> {
                    log.info("Создаем нового пользователя: {}", login);
                    return userRepository.save(new CompanyUser(login));
                });
    }

    private LocalDateTime parseDateTime(String dateTimeString) {
        try {
            // Убираем часовой пояс если есть
            String cleanDateTime = dateTimeString.replaceAll(" [A-Z]{3}$", "");
            return LocalDateTime.parse(cleanDateTime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception e) {
            log.warn("Не удалось распарсить дату: {}, используем текущее время", dateTimeString);
            return LocalDateTime.now();
        }
    }
}