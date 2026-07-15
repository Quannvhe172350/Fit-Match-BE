package com.fitmatch.repository;

import com.fitmatch.entity.DisputeEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DisputeEvidenceRepository extends JpaRepository<DisputeEvidence, Long> {

    List<DisputeEvidence> findByDispute_IdOrderByIdAsc(Long disputeId);
}
