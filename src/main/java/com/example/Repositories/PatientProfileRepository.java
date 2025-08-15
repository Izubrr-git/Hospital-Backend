package com.example.Repositories;

import com.example.JPA_Entities.PatientProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PatientProfileRepository extends JpaRepository<PatientProfile, Long> {
    @Query("SELECT p FROM PatientProfile p WHERE p.statusId IN (200, 210, 230)")
    List<PatientProfile> findActivePatients();

    @Query("SELECT p FROM PatientProfile p WHERE p.oldClientGuid LIKE %:clientGuid%")
    List<PatientProfile> findByOldClientGuidContaining(@Param("clientGuid") String clientGuid);
}
