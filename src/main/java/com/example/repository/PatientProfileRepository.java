package com.example.repository;

import com.example.entity.PatientProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PatientProfileRepository extends JpaRepository<PatientProfile, Long> {

    @Query("SELECT p FROM PatientProfile p WHERE p.statusId IN (200, 210, 230)")
    List<PatientProfile> findActivePatients();

    // Более эффективный поиск по GUID
    @Query("SELECT p FROM PatientProfile p WHERE p.oldClientGuid LIKE CONCAT('%', :clientGuid, '%')")
    List<PatientProfile> findByOldClientGuidContaining(@Param("clientGuid") String clientGuid);

    // Точный поиск по конкретному GUID
    @Query("SELECT p FROM PatientProfile p WHERE " +
            "p.oldClientGuid = :clientGuid OR " +
            "p.oldClientGuid LIKE CONCAT(:clientGuid, ',%') OR " +
            "p.oldClientGuid LIKE CONCAT('%,', :clientGuid) OR " +
            "p.oldClientGuid LIKE CONCAT('%,', :clientGuid, ',%')")
    List<PatientProfile> findByExactOldClientGuid(@Param("clientGuid") String clientGuid);

    // Поиск пациентов без старых GUID
    @Query("SELECT p FROM PatientProfile p WHERE p.oldClientGuid IS NULL OR p.oldClientGuid = ''")
    List<PatientProfile> findPatientsWithoutOldGuids();

    // Статистика активных пациентов
    @Query("SELECT COUNT(p) FROM PatientProfile p WHERE p.statusId IN (200, 210, 230)")
    long countActivePatients();

    // Пациенты по статусу
    List<PatientProfile> findByStatusId(Short statusId);
}