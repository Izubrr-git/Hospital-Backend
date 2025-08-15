package com.example;

import com.example.Services.ImportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class NotesImportScheduler {
    private final ImportService importService;

    public NotesImportScheduler(ImportService importService) {
        this.importService = importService;
    }

    @Scheduled(cron = "0 15 */2 * * *") // Каждые 2 часа в 15 минут
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
