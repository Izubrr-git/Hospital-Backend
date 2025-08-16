package com.example.scheduler;

import com.example.model.ImportStatistics;
import com.example.service.EnhancedImportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class NotesImportScheduler {
    private final EnhancedImportService importService;

    public NotesImportScheduler(EnhancedImportService enhancedImportService) {
        this.importService = enhancedImportService;
    }

    @Scheduled(cron = "0 15 */2 * * *")
    public void scheduleImport() {
        log.info("Запуск планового импорта заметок");
        try {
            ImportStatistics stats = importService.performImport();
            if (stats.isHasCriticalError() || stats.getErrorCount() > 0) {
                log.warn("Импорт завершен с ошибками: {}", stats);
            } else {
                log.info("Импорт успешно завершен: {}", stats);
            }
        } catch (Exception e) {
            log.error("Критическая ошибка при выполнении планового импорта", e);
        }
    }
}
