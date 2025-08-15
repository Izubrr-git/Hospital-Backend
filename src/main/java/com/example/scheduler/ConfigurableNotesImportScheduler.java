package com.example.scheduler;

import com.example.config.ImportConfigProperties;
import com.example.model.ImportStatistics;
import com.example.monitoring.ImportMetrics;
import com.example.service.EnhancedImportService;
import com.example.service.ParallelImportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
@Slf4j
public class ConfigurableNotesImportScheduler {

    private final EnhancedImportService importService;
    private final ParallelImportService parallelImportService;
    private final ImportConfigProperties config;
    private final ImportMetrics metrics;

    public ConfigurableNotesImportScheduler(EnhancedImportService importService,
                                            ParallelImportService parallelImportService,
                                            ImportConfigProperties config,
                                            ImportMetrics metrics) {
        this.importService = importService;
        this.parallelImportService = parallelImportService;
        this.config = config;
        this.metrics = metrics;
    }

    @Scheduled(cron = "${notes.import.schedule:0 15 */2 * * *}")
    public void scheduleImport() {
        log.info("Запуск планового импорта заметок");
        metrics.recordImportStarted();

        ImportStatistics stats;
        Instant startTime = Instant.now();

        try {
            if (config.isEnableParallelProcessing()) {
                log.info("Используем параллельную обработку с {} потоками", config.getParallelThreads());
                stats = parallelImportService.performParallelImport();
            } else {
                log.info("Используем последовательную обработку");
                stats = importService.performImport();
            }

            Duration duration = Duration.between(startTime, Instant.now());
            boolean success = !stats.isHasCriticalError();

            metrics.recordImportCompleted(duration, success);
            metrics.recordNotesProcessed(stats.getCreatedCount(),
                    stats.getUpdatedCount(),
                    stats.getSkippedCount());

            if (success) {
                log.info("Импорт успешно завершен: {}", stats);
            } else {
                log.warn("Импорт завершен с ошибками: {}", stats);
                metrics.recordImportError("critical_error");
            }

        } catch (Exception e) {
            log.error("Критическая ошибка при выполнении планового импорта", e);
            metrics.recordImportError("exception");
            Duration duration = Duration.between(startTime, Instant.now());
            metrics.recordImportCompleted(duration, false);
        }
    }

    // Метод для изменения расписания во время выполнения
    @EventListener
    public void handleConfigurationChange(ConfigurationChangeEvent event) {
        log.info("Конфигурация импорта изменена: {}", event);
        // Здесь можно добавить логику для динамического изменения расписания
    }

    // Дополнительные методы для управления

    public void triggerManualImport() {
        log.info("Запуск ручного импорта через планировщик");
        scheduleImport();
    }

    public boolean isParallelProcessingEnabled() {
        return config.isEnableParallelProcessing();
    }

    public String getCurrentSchedule() {
        return config.getSchedule();
    }
}
