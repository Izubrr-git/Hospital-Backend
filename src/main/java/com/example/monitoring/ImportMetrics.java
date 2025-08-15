package com.example.monitoring;

import com.example.model.ImportStatistics;
import io.micrometer.core.instrument.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Slf4j
public class ImportMetrics {

    private final MeterRegistry meterRegistry;

    // Counters
    private final Counter importStartedCounter;
    private final Counter importCompletedCounter;
    private final Counter importErrorCounter;
    private final Counter notesCreatedCounter;
    private final Counter notesUpdatedCounter;
    private final Counter notesSkippedCounter;
    private final Counter usersCreatedCounter;

    // Timers - используем один базовый таймер
    private final Timer importDurationTimer;

    // Gauges - храним атомарные значения
    private final AtomicLong lastImportTimestamp = new AtomicLong(0);
    private final AtomicLong activeImportsCount = new AtomicLong(0);
    private final AtomicLong totalNotesProcessed = new AtomicLong(0);
    private final AtomicLong totalErrorsCount = new AtomicLong(0);

    public ImportMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        // Инициализация счетчиков
        this.importStartedCounter = Counter.builder("notes_import_started_total")
                .description("Общее количество запущенных операций импорта")
                .register(meterRegistry);

        this.importCompletedCounter = Counter.builder("notes_import_completed_total")
                .description("Общее количество завершенных операций импорта")
                .register(meterRegistry);

        this.importErrorCounter = Counter.builder("notes_import_errors_total")
                .description("Общее количество ошибок импорта")
                .register(meterRegistry);

        this.notesCreatedCounter = Counter.builder("notes_import_notes_created_total")
                .description("Общее количество созданных заметок")
                .register(meterRegistry);

        this.notesUpdatedCounter = Counter.builder("notes_import_notes_updated_total")
                .description("Общее количество обновленных заметок")
                .register(meterRegistry);

        this.notesSkippedCounter = Counter.builder("notes_import_notes_skipped_total")
                .description("Общее количество пропущенных заметок")
                .register(meterRegistry);

        this.usersCreatedCounter = Counter.builder("notes_import_users_created_total")
                .description("Общее количество созданных пользователей")
                .register(meterRegistry);

        // Инициализация таймеров
        this.importDurationTimer = Timer.builder("notes_import_duration_seconds")
                .description("Время выполнения операций импорта")
                .register(meterRegistry);

        // Инициализация gauge метрик - ПРАВИЛЬНЫЙ СПОСОБ
        meterRegistry.gauge("notes_import_last_execution_timestamp",
                Tags.of(Tag.of("description", "Timestamp последнего выполнения импорта")),
                lastImportTimestamp,
                value -> value.get() / 1000.0);

        meterRegistry.gauge("notes_import_active_operations_count",
                Tags.of(Tag.of("description", "Количество активных операций импорта")),
                activeImportsCount,
                AtomicLong::get);

        meterRegistry.gauge("notes_import_total_notes_processed",
                Tags.of(Tag.of("description", "Общее количество обработанных заметок")),
                totalNotesProcessed,
                AtomicLong::get);

        meterRegistry.gauge("notes_import_total_errors",
                Tags.of(Tag.of("description", "Общее количество ошибок")),
                totalErrorsCount,
                AtomicLong::get);
    }

    // Методы для записи метрик

    public void recordImportStarted() {
        importStartedCounter.increment();
        activeImportsCount.incrementAndGet();
        log.debug("Записана метрика: импорт запущен");
    }

    public void recordImportCompleted(Duration duration, boolean success) {
        importCompletedCounter.increment();
        activeImportsCount.decrementAndGet();

        if (success) {
            importDurationTimer.record(duration);
            lastImportTimestamp.set(System.currentTimeMillis());
            log.debug("Записана метрика: импорт завершен успешно, продолжительность: {}", duration);
        } else {
            log.debug("Записана метрика: импорт завершен с ошибкой, продолжительность: {}", duration);
        }
    }

    public void recordImportError(String errorType) {
        importErrorCounter.increment();
        totalErrorsCount.incrementAndGet();
        log.debug("Записана метрика: ошибка импорта типа '{}'", errorType);
    }

    public void recordNotesProcessed(int created, int updated, int skipped) {
        if (created > 0) {
            notesCreatedCounter.increment(created);
            totalNotesProcessed.addAndGet(created);
        }
        if (updated > 0) {
            notesUpdatedCounter.increment(updated);
            totalNotesProcessed.addAndGet(updated);
        }
        if (skipped > 0) {
            notesSkippedCounter.increment(skipped);
        }

        log.debug("Записаны метрики заметок: создано={}, обновлено={}, пропущено={}",
                created, updated, skipped);
    }

    public void recordUserCreated() {
        usersCreatedCounter.increment();
        log.debug("Записана метрика: пользователь создан");
    }

    // ИСПРАВЛЕННЫЕ МЕТОДЫ ДЛЯ TIMER SAMPLE

    public Timer.Sample startApiCallTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordApiCall(Timer.Sample sample, String operation, boolean success) {
        // Правильный способ для Micrometer
        Timer apiTimer = Timer.builder("notes_import_api_call_duration_seconds")
                .description("Время выполнения вызовов Legacy API")
                .tag("operation", operation)
                .tag("status", success ? "success" : "failure")
                .register(meterRegistry);

        sample.stop(apiTimer);
        log.debug("Записана метрика API вызова: операция='{}', успех={}", operation, success);
    }

    public Timer.Sample startDatabaseTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordDatabaseOperation(Timer.Sample sample, String operation, boolean success) {
        // Правильный способ для Micrometer
        Timer dbTimer = Timer.builder("notes_import_database_operation_duration_seconds")
                .description("Время выполнения операций с базой данных")
                .tag("operation", operation)
                .tag("status", success ? "success" : "failure")
                .register(meterRegistry);

        sample.stop(dbTimer);
        log.debug("Записана метрика БД операции: операция='{}', успех={}", operation, success);
    }

    // АЛЬТЕРНАТИВНЫЙ ПОДХОД - используем кэшированные таймеры
    private final Map<String, Timer> apiTimers = new HashMap<>();
    private final Map<String, Timer> dbTimers = new HashMap<>();

    public void recordApiCallCached(Timer.Sample sample, String operation, boolean success) {
        String timerKey = operation + "_" + (success ? "success" : "failure");
        Timer timer = apiTimers.computeIfAbsent(timerKey, key ->
                Timer.builder("notes_import_api_call_duration_seconds_cached")
                        .description("Время выполнения вызовов Legacy API (кэшированный)")
                        .tag("operation", operation)
                        .tag("status", success ? "success" : "failure")
                        .register(meterRegistry)
        );

        sample.stop(timer);
        log.debug("Записана кэшированная метрика API: операция='{}', успех={}", operation, success);
    }

    public void recordDatabaseOperationCached(Timer.Sample sample, String operation, boolean success) {
        String timerKey = operation + "_" + (success ? "success" : "failure");
        Timer timer = dbTimers.computeIfAbsent(timerKey, key ->
                Timer.builder("notes_import_database_operation_duration_seconds_cached")
                        .description("Время выполнения операций с базой данных (кэшированный)")
                        .tag("operation", operation)
                        .tag("status", success ? "success" : "failure")
                        .register(meterRegistry)
        );

        sample.stop(timer);
        log.debug("Записана кэшированная метрика БД: операция='{}', успех={}", operation, success);
    }

    // Методы для получения текущих значений метрик

    public double getLastImportTimestamp() {
        return lastImportTimestamp.get() / 1000.0; // Конвертируем в секунды для Prometheus
    }

    public double getActiveImportsCount() {
        return activeImportsCount.get();
    }

    public double getTotalNotesProcessed() {
        return totalNotesProcessed.get();
    }

    public double getTotalErrorsCount() {
        return totalErrorsCount.get();
    }

    // Методы для получения статистики

    public ImportStatistics getCurrentStatistics() {
        ImportStatistics stats = new ImportStatistics();

        // Получаем значения счетчиков
        stats.setCreatedCount((int) notesCreatedCounter.count());
        stats.setUpdatedCount((int) notesUpdatedCounter.count());
        stats.setSkippedCount((int) notesSkippedCounter.count());
        stats.setErrorCount((int) totalErrorsCount.get());

        if (lastImportTimestamp.get() > 0) {
            stats.setEndTime(LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(lastImportTimestamp.get()),
                    ZoneId.systemDefault()
            ));
        }

        return stats;
    }

    public Map<String, Double> getAllMetrics() {
        Map<String, Double> metrics = new HashMap<>();

        metrics.put("import_started_total", importStartedCounter.count());
        metrics.put("import_completed_total", importCompletedCounter.count());
        metrics.put("import_errors_total", importErrorCounter.count());
        metrics.put("notes_created_total", notesCreatedCounter.count());
        metrics.put("notes_updated_total", notesUpdatedCounter.count());
        metrics.put("notes_skipped_total", notesSkippedCounter.count());
        metrics.put("users_created_total", usersCreatedCounter.count());
        metrics.put("active_imports_count", getActiveImportsCount());
        metrics.put("total_notes_processed", getTotalNotesProcessed());
        metrics.put("last_import_timestamp", getLastImportTimestamp());

        return metrics;
    }

    // Методы для сброса метрик (для тестирования)

    @EventListener
    @ConditionalOnProperty(name = "spring.profiles.active", havingValue = "test")
    public void resetMetricsForTesting() {
        log.warn("Сброс метрик для тестирования");

        activeImportsCount.set(0);
        totalNotesProcessed.set(0);
        totalErrorsCount.set(0);
        lastImportTimestamp.set(0);
    }

    // Периодическое логирование статистики

    @Scheduled(fixedRate = 300000) // Каждые 5 минут
    @ConditionalOnProperty(name = "notes.import.enable-detailed-logging", havingValue = "true")
    public void logPeriodicStatistics() {
        Map<String, Double> metrics = getAllMetrics();
        log.info("Статистика импорта: {}", metrics);
    }

    // Методы для очистки кэшей (для production)

    public void clearTimerCaches() {
        apiTimers.clear();
        dbTimers.clear();
        log.info("Кэши таймеров очищены");
    }

    public int getApiTimerCacheSize() {
        return apiTimers.size();
    }

    public int getDatabaseTimerCacheSize() {
        return dbTimers.size();
    }
}