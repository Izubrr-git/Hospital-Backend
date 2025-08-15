package com.example.service;

import com.example.config.ImportConfigProperties;
import com.example.dto.LegacyClient;
import com.example.dto.LegacyNote;
import com.example.monitoring.ImportMetrics;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;

@Component
@Slf4j
public class RetryableApiService {

    private final LegacyApiService legacyApiService;
    private final ImportConfigProperties config;
    private final ImportMetrics metrics;

    public RetryableApiService(LegacyApiService legacyApiService,
                               ImportConfigProperties config,
                               ImportMetrics metrics) {
        this.legacyApiService = legacyApiService;
        this.config = config;
        this.metrics = metrics;
    }

    public List<LegacyClient> getAllClientsWithRetry() throws Exception {
        return executeWithRetry("getAllClients",
                () -> {
                    Timer.Sample sample = metrics.startApiCallTimer();
                    try {
                        List<LegacyClient> result = legacyApiService.getAllClients();
                        metrics.recordApiCall(sample, "getAllClients", true);
                        return result;
                    } catch (Exception e) {
                        metrics.recordApiCall(sample, "getAllClients", false);
                        throw e;
                    }
                });
    }

    public List<LegacyNote> getClientNotesWithRetry(String agency, String clientGuid,
                                                    LocalDate dateFrom, LocalDate dateTo) throws Exception {
        return executeWithRetry("getClientNotes for " + clientGuid,
                () -> {
                    Timer.Sample sample = metrics.startApiCallTimer();
                    try {
                        List<LegacyNote> result = legacyApiService.getClientNotes(agency, clientGuid, dateFrom, dateTo);
                        metrics.recordApiCall(sample, "getClientNotes", true);
                        return result;
                    } catch (Exception e) {
                        metrics.recordApiCall(sample, "getClientNotes", false);
                        throw e;
                    }
                });
    }

    private <T> T executeWithRetry(String operationName, Callable<T> operation) throws Exception {
        int attempts = 0;
        Exception lastException = null;

        while (attempts < config.getMaxRetryAttempts()) {
            try {
                return operation.call();
            } catch (Exception e) {
                attempts++;
                lastException = e;

                if (attempts < config.getMaxRetryAttempts()) {
                    log.warn("Ошибка выполнения {}, попытка {}/{}: {}. Повтор через {} сек.",
                            operationName, attempts, config.getMaxRetryAttempts(),
                            e.getMessage(), config.getRetryDelaySeconds());

                    metrics.recordImportError("retry_attempt");

                    try {
                        Thread.sleep(config.getRetryDelayMillis());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Операция прервана", ie);
                    }
                } else {
                    log.error("Исчерпаны все попытки выполнения {}: {}", operationName, e.getMessage());
                    metrics.recordImportError("max_retries_exceeded");
                }
            }
        }

        throw new RuntimeException("Не удалось выполнить " + operationName + " после " +
                config.getMaxRetryAttempts() + " попыток", lastException);
    }

    // Дополнительные методы для работы с API

    public boolean testApiConnection() {
        try {
            Timer.Sample sample = metrics.startApiCallTimer();
            try {
                List<LegacyClient> clients = legacyApiService.getAllClients();
                metrics.recordApiCall(sample, "testConnection", true);
                log.info("Тест подключения к Legacy API успешен. Получено {} клиентов", clients.size());
                return true;
            } catch (Exception e) {
                metrics.recordApiCall(sample, "testConnection", false);
                log.error("Тест подключения к Legacy API неуспешен", e);
                return false;
            }
        } catch (Exception e) {
            log.error("Критическая ошибка при тестировании API", e);
            return false;
        }
    }

    public int getConfiguredRetryAttempts() {
        return config.getMaxRetryAttempts();
    }

    public long getConfiguredRetryDelay() {
        return config.getRetryDelayMillis();
    }
}
