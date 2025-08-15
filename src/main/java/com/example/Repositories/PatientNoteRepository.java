package com.example.Repositories;

import com.example.JPA_Entities.PatientNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PatientNoteRepository extends JpaRepository<PatientNote, Long> {
    Optional<PatientNote> findByLegacyNoteGuid(String legacyNoteGuid);

    @Query("SELECT COUNT(pn) FROM PatientNote pn WHERE pn.legacyNoteGuid IS NOT NULL")
    long countImportedNotes();
}
