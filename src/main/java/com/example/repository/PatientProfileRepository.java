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

    @Query("SELECT p FROM PatientProfile p WHERE p.oldClientGuid LIKE CONCAT('%', :clientGuid, '%')")
    List<PatientProfile> findByOldClientGuidContaining(@Param("clientGuid") String clientGuid);

    @Query("SELECT p FROM PatientProfile p WHERE " +
            "p.oldClientGuid = :clientGuid OR " +
            "p.oldClientGuid LIKE CONCAT(:clientGuid, ',%') OR " +
            "p.oldClientGuid LIKE CONCAT('%,', :clientGuid) OR " +
            "p.oldClientGuid LIKE CONCAT('%,', :clientGuid, ',%')")
    List<PatientProfile> findByExactOldClientGuid(@Param("clientGuid") String clientGuid);

    @Query("SELECT p FROM PatientProfile p WHERE p.oldClientGuid IS NULL OR p.oldClientGuid = ''")
    List<PatientProfile> findPatientsWithoutOldGuids();

    @Query("SELECT COUNT(p) FROM PatientProfile p WHERE p.statusId IN (200, 210, 230)")
    long countActivePatients();

    List<PatientProfile> findByStatusId(Short statusId);
}