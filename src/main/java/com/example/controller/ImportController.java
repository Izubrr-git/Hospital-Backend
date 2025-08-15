package com.example.controller;

import com.example.model.ImportStatistics;
import com.example.service.EnhancedImportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/import")
@Slf4j
public class ImportController {

    private final EnhancedImportService importService;

    public ImportController(EnhancedImportService importService) {
        this.importService = importService;
    }

    @PostMapping("/start")
    public ResponseEntity<ImportStatistics> startManualImport() {
        log.info("Запуск ручного импорта через API");
        try {
            ImportStatistics stats = importService.performImport();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Ошибка при ручном импорте", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorStats(e.getMessage()));
        }
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getImportStatus() {
        Map<String, Object> status = new HashMap<>();

        try {
            // Получаем статистику из базы данных
            long totalNotes = importService.getTotalNotesCount();
            long importedNotes = importService.getImportedNotesCount();
            LocalDateTime lastImport = importService.getLastImportTime();

            status.put("totalNotes", totalNotes);
            status.put("importedNotes", importedNotes);
            status.put("lastImportTime", lastImport);
            status.put("importProgress", totalNotes > 0 ? (double) importedNotes / totalNotes : 0.0);

            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("Ошибка получения статуса импорта", e);
            status.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(status);
        }
    }

    @PostMapping("/patient/{patientId}")
    public ResponseEntity<ImportStatistics> importSpecificPatient(@PathVariable Long patientId) {
        log.info("Запуск импорта для конкретного пациента: {}", patientId);
        try {
            ImportStatistics stats = importService.importSpecificPatient(patientId);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Ошибка при импорте для пациента {}", patientId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorStats(e.getMessage()));
        }
    }

    @GetMapping("/statistics")
    public ResponseEntity<ImportStatistics> getImportStatistics() {
        try {
            ImportStatistics stats = importService.getImportStatistics();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Ошибка получения статистики", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorStats(e.getMessage()));
        }
    }

    private ImportStatistics createErrorStats(String errorMessage) {
        ImportStatistics stats = new ImportStatistics();
        stats.setStartTime(LocalDateTime.now());
        stats.setEndTime(LocalDateTime.now());
        stats.setHasCriticalError(true);
        stats.incrementErrorCount();
        return stats;
    }
}
