package com.example;

import lombok.Data;

import java.time.Duration;
import java.time.LocalDateTime;

@Data
public class ImportStatistics {
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private int createdCount = 0;
    private int updatedCount = 0;
    private int skippedCount = 0;
    private int errorCount = 0;
    private boolean hasCriticalError = false;

    public void incrementCreatedCount() { createdCount++; }
    public void incrementUpdatedCount() { updatedCount++; }
    public void incrementSkippedCount() { skippedCount++; }
    public void incrementErrorCount() { errorCount++; }

    public Duration getDuration() {
        if (startTime != null && endTime != null) {
            return Duration.between(startTime, endTime);
        }
        return Duration.ZERO;
    }

    @Override
    public String toString() {
        return String.format("ImportStatistics{создано=%d, обновлено=%d, пропущено=%d, ошибок=%d, время=%s}",
                createdCount, updatedCount, skippedCount, errorCount, getDuration());
    }
}
