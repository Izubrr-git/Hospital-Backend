package com.example.repository;

import com.example.entity.PatientNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PatientNoteRepository extends JpaRepository<PatientNote, Long> {

    Optional<PatientNote> findByLegacyNoteGuid(String legacyNoteGuid);

    @Query("SELECT COUNT(pn) FROM PatientNote pn WHERE pn.legacyNoteGuid IS NOT NULL")
    long countImportedNotes();

    // Метод для получения последней импортированной заметки
    Optional<PatientNote> findTopByLegacyNoteGuidIsNotNullOrderByLastModifiedDateTimeDesc();

    // Поиск заметок по пациенту и периоду
    @Query("SELECT pn FROM PatientNote pn WHERE pn.patient.id = :patientId " +
            "AND pn.createdDateTime BETWEEN :dateFrom AND :dateTo " +
            "ORDER BY pn.createdDateTime DESC")
    List<PatientNote> findByPatientAndDateRange(@Param("patientId") Long patientId,
                                                @Param("dateFrom") LocalDateTime dateFrom,
                                                @Param("dateTo") LocalDateTime dateTo);

    // Статистика по импорту
    @Query("SELECT COUNT(pn) FROM PatientNote pn WHERE pn.legacyNoteGuid IS NOT NULL " +
            "AND pn.lastModifiedDateTime >= :since")
    long countImportedSince(@Param("since") LocalDateTime since);

    // Поиск дублированных заметок
    @Query("SELECT pn.legacyNoteGuid, COUNT(pn) FROM PatientNote pn " +
            "WHERE pn.legacyNoteGuid IS NOT NULL " +
            "GROUP BY pn.legacyNoteGuid HAVING COUNT(pn) > 1")
    List<Object[]> findDuplicateLegacyGuids();
}
