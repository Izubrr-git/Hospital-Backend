package com.example.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = true)
public class EnhancedImportStatistics extends ImportStatistics {

    private Map<String, Integer> errorsByType = new HashMap<>();
    private Set<String> processedPatients = new HashSet<>();
    private Set<String> skippedPatients = new HashSet<>();
    private Map<String, Integer> userCreationStats = new HashMap<>();
    private long apiCallsCount = 0;
    private long databaseOperationsCount = 0;
    private Duration totalApiTime = Duration.ZERO;
    private Duration totalDatabaseTime = Duration.ZERO;

    public void addError(String errorType) {
        errorsByType.merge(errorType, 1, Integer::sum);
        incrementErrorCount();
    }

    public void addProcessedPatient(String patientId) {
        processedPatients.add(patientId);
    }

    public void addSkippedPatient(String patientId) {
        skippedPatients.add(patientId);
    }

    public void addUserCreated(String userType) {
        userCreationStats.merge(userType, 1, Integer::sum);
    }

    public void addApiCall(Duration duration) {
        apiCallsCount++;
        totalApiTime = totalApiTime.plus(duration);
    }

    public void addDatabaseOperation(Duration duration) {
        databaseOperationsCount++;
        totalDatabaseTime = totalDatabaseTime.plus(duration);
    }

    public void merge(ImportStatistics other) {
        this.createdCount += other.getCreatedCount();
        this.updatedCount += other.getUpdatedCount();
        this.skippedCount += other.getSkippedCount();
        this.errorCount += other.getErrorCount();

        if (other instanceof EnhancedImportStatistics enhanced) {
            enhanced.errorsByType.forEach((type, count) ->
                    this.errorsByType.merge(type, count, Integer::sum));
            this.processedPatients.addAll(enhanced.processedPatients);
            this.skippedPatients.addAll(enhanced.skippedPatients);
            enhanced.userCreationStats.forEach((type, count) ->
                    this.userCreationStats.merge(type, count, Integer::sum));
            this.apiCallsCount += enhanced.apiCallsCount;
            this.databaseOperationsCount += enhanced.databaseOperationsCount;
            this.totalApiTime = this.totalApiTime.plus(enhanced.totalApiTime);
            this.totalDatabaseTime = this.totalDatabaseTime.plus(enhanced.totalDatabaseTime);
        }
    }

    public double getAverageApiCallTime() {
        return apiCallsCount > 0 ? totalApiTime.toMillis() / (double) apiCallsCount : 0.0;
    }

    public double getAverageDatabaseOperationTime() {
        return databaseOperationsCount > 0 ? totalDatabaseTime.toMillis() / (double) databaseOperationsCount : 0.0;
    }

    @Override
    public String toString() {
        return String.format(
                "EnhancedImportStatistics{создано=%d, обновлено=%d, пропущено=%d, ошибок=%d, " +
                        "обработано пациентов=%d, пропущено пациентов=%d, API вызовов=%d, БД операций=%d, " +
                        "время=%s, ошибки по типам=%s, создано пользователей=%s}",
                createdCount, updatedCount, skippedCount, errorCount,
                processedPatients.size(), skippedPatients.size(),
                apiCallsCount, databaseOperationsCount,
                getDuration(), errorsByType, userCreationStats);
    }
}

