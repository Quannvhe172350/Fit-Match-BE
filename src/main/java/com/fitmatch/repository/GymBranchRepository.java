package com.fitmatch.repository;

import com.fitmatch.entity.GymBranch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GymBranchRepository extends JpaRepository<GymBranch, Long> {

    List<GymBranch> findByGymProfile_Id(Long gymProfileId);

    /** UC-009: chi nhánh đang hoạt động cho trang public. */
    List<GymBranch> findByGymProfile_IdAndActiveTrue(Long gymProfileId);

    Optional<GymBranch> findByIdAndGymProfile_User_Username(Long id, String username);
}
