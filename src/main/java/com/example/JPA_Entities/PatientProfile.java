package com.example.JPA_Entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Entity
@Table(name = "patient_profile")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PatientProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "old_client_guid")
    private String oldClientGuid;

    @Column(name = "status_id", nullable = false)
    private Short statusId;

    // Метод для проверки активного статуса
    public boolean isActive() {
        return statusId != null && Arrays.asList((short)200, (short)210, (short)230).contains(statusId);
    }

    // Метод для получения списка старых GUID
    public List<String> getOldClientGuids() {
        if (oldClientGuid == null || oldClientGuid.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(oldClientGuid.split(","))
                .map(String::trim)
                .filter(guid -> !guid.isEmpty())
                .collect(Collectors.toList());
    }
}
