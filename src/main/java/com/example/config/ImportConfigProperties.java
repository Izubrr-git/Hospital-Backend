package com.example.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import javax.validation.constraints.Min;
import javax.validation.constraints.Max;
import javax.validation.constraints.NotBlank;

@ConfigurationProperties(prefix = "notes.import")
@Data
@Component
@Validated
public class ImportConfigProperties {
    @Min(1)
    @Max(10000)
    private int patientBatchSize = 100;

    @Min(1)
    @Max(365)
    private int daysToImport = 30;

    @Min(1)
    @Max(20)
    private int maxRetryAttempts = 3;

    @Min(1)
    @Max(300)
    private int retryDelaySeconds = 5;

    private boolean enableParallelProcessing = true;

    @Min(1)
    @Max(50)
    private int parallelThreads = 4;

    @Min(1)
    @Max(720)
    private int operationTimeoutMinutes = 60;

    @NotBlank
    private String schedule = "0 15 */2 * * *";

    @Min(100)
    @Max(10000)
    private int maxNoteLength = 4000;

    private boolean enableDataValidation = true;

    private boolean enableBackup = false;

    private String backupPath = "/var/backups/hospital-backend";

    @Min(1)
    @Max(100)
    private int maxConcurrentApiConnections = 10;

    private boolean enableDetailedLogging = false;

    @PostConstruct
    public void validateConfiguration() {
        if (enableParallelProcessing && parallelThreads > patientBatchSize) {
            throw new IllegalStateException(
                    "Количество потоков не может быть больше размера batch: " +
                            "parallelThreads=" + parallelThreads + ", patientBatchSize=" + patientBatchSize
            );
        }

        if (enableBackup && (backupPath == null || backupPath.trim().isEmpty())) {
            throw new IllegalStateException("Путь для резервных копий не может быть пустым при включенном backup");
        }
    }

    public long getOperationTimeoutMillis() {
        return operationTimeoutMinutes * 60L * 1000L;
    }

    public long getRetryDelayMillis() {
        return retryDelaySeconds * 1000L;
    }

    public boolean isDebugMode() {
        return enableDetailedLogging;
    }

    @Override
    public String toString() {
        return "ImportConfigProperties{" +
                "patientBatchSize=" + patientBatchSize +
                ", daysToImport=" + daysToImport +
                ", maxRetryAttempts=" + maxRetryAttempts +
                ", retryDelaySeconds=" + retryDelaySeconds +
                ", enableParallelProcessing=" + enableParallelProcessing +
                ", parallelThreads=" + parallelThreads +
                ", operationTimeoutMinutes=" + operationTimeoutMinutes +
                ", schedule='" + schedule + '\'' +
                '}';
    }
}
