package com.fitmatch.repository;

import com.fitmatch.entity.PtAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PtAssignmentRepository extends JpaRepository<PtAssignment, Long> {

    List<PtAssignment> findByPtProfile_Id(Long ptProfileId);

    Optional<PtAssignment> findByIdAndPtProfile_GymProfile_User_Username(Long id, String username);

    boolean existsByPtProfile_IdAndGymBranch_Id(Long ptId, Long branchId);

    boolean existsByPtProfile_IdAndGymService_Id(Long ptId, Long serviceId);

    boolean existsByPtProfile_IdAndTrainingPackage_Id(Long ptId, Long packageId);
}
