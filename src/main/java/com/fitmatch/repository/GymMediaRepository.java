package com.fitmatch.repository;

import com.fitmatch.entity.GymMedia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GymMediaRepository extends JpaRepository<GymMedia, Long> {

    List<GymMedia> findByGymProfile_Id(Long gymProfileId);

    List<GymMedia> findByGymBranch_Id(Long branchId);

    /** Ownership check theo operator đang đăng nhập (chống IDOR). */
    Optional<GymMedia> findByIdAndGymProfile_User_Username(Long id, String username);
}
