package com.example.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "patient_note")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PatientNote {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_date_time", nullable = false)
    private LocalDateTime createdDateTime;

    @Column(name = "last_modified_date_time", nullable = false)
    private LocalDateTime lastModifiedDateTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private CompanyUser createdByUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_modified_by_user_id")
    private CompanyUser lastModifiedByUser;

    @Column(name = "note", length = 4000)
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private PatientProfile patient;

    // Поле для хранения GUID из старой системы
    @Column(name = "legacy_note_guid", unique = true)
    private String legacyNoteGuid;
}
