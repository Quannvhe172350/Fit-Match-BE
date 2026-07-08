package com.fitmatch.repository;

import com.fitmatch.entity.WaitlistEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WaitlistEntryRepository extends JpaRepository<WaitlistEntry, Long> {

    List<WaitlistEntry> findByCustomer_UsernameAndActiveTrueOrderByCreatedAtDesc(String username);

    Optional<WaitlistEntry> findByIdAndCustomer_Username(Long id, String username);

    List<WaitlistEntry> findByGymService_IdAndActiveTrueOrderByCreatedAtAsc(Long serviceId);

    List<WaitlistEntry> findByTrainingPackage_IdAndActiveTrueOrderByCreatedAtAsc(Long packageId);
}
