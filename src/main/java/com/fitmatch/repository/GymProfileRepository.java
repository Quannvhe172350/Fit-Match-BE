package com.fitmatch.repository;

import com.fitmatch.common.enums.VerificationStatus;
import com.fitmatch.entity.GymProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GymProfileRepository extends JpaRepository<GymProfile, Long>, JpaSpecificationExecutor<GymProfile> {

    Optional<GymProfile> findByUser_Username(String username);

    Optional<GymProfile> findByUser_UsernameAndVerificationStatus(String username, VerificationStatus status);

    boolean existsByUser_Username(String username);

    Page<GymProfile> findByVerificationStatus(VerificationStatus status, Pageable pageable);

    Optional<GymProfile> findByIdAndVerificationStatusAndActiveTrue(Long id, VerificationStatus status);
}
