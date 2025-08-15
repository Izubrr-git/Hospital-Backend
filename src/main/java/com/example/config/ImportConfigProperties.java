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

    /**
     * Размер batch для обработки пациентов
     */
    @Min(1)
    @Max(10000)
    private int patientBatchSize = 100;

    /**
     * Количество дней для загрузки заметок (от текущей даты назад)
     */
    @Min(1)
    @Max(365)
    private int daysToImport = 30;

    /**
     * Максимальное количество повторных попыток при ошибке API
     */
    @Min(1)
    @Max(20)
    private int maxRetryAttempts = 3;

    /**
     * Задержка между повторными попытками (в секундах)
     */
    @Min(1)
    @Max(300)
    private int retryDelaySeconds = 5;

    /**
     * Включить ли параллельную обработку
     */
    private boolean enableParallelProcessing = true;

    /**
     * Количество потоков для параллельной обработки
     */
    @Min(1)
    @Max(50)
    private int parallelThreads = 4;

    /**
     * Таймаут для одной операции импорта (в минутах)
     */
    @Min(1)
    @Max(720)
    private int operationTimeoutMinutes = 60;

    /**
     * Cron выражение для расписания импорта
     */
    @NotBlank
    private String schedule = "0 15 */2 * * *";

    /**
     * Максимальный размер одной заметки в символах
     */
    @Min(100)
    @Max(10000)
    private int maxNoteLength = 4000;

    /**
     * Включить ли валидацию данных перед импортом
     */
    private boolean enableDataValidation = true;

    /**
     * Включить ли создание резервных копий перед импортом
     */
    private boolean enableBackup = false;

    /**
     * Путь для хранения резервных копий
     */
    private String backupPath = "/var/backups/hospital-backend";

    /**
     * Максимальное количество одновременных подключений к Legacy API
     */
    @Min(1)
    @Max(100)
    private int maxConcurrentApiConnections = 10;

    /**
     * Включить ли детальное логирование процесса импорта
     */
    private boolean enableDetailedLogging = false;

    // Валидация конфигурации
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

    /**
     * Получить максимальное время ожидания в миллисекундах
     */
    public long getOperationTimeoutMillis() {
        return operationTimeoutMinutes * 60L * 1000L;
    }

    /**
     * Получить задержку retry в миллисекундах
     */
    public long getRetryDelayMillis() {
        return retryDelaySeconds * 1000L;
    }

    /**
     * Проверить, включен ли debug режим
     */
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
