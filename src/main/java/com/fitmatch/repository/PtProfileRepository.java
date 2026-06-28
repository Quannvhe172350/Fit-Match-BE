package com.fitmatch.repository;

import com.fitmatch.common.enums.VerificationStatus;
import com.fitmatch.entity.PtProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PtProfileRepository extends JpaRepository<PtProfile, Long>, JpaSpecificationExecutor<PtProfile> {

    Optional<PtProfile> findByUser_Username(String username);

    boolean existsByUser_Username(String username);

    Optional<PtProfile> findByIdAndVerificationStatusAndActiveTrue(Long id, VerificationStatus status);
}
