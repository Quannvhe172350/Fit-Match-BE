package com.fitmatch.repository;

import com.fitmatch.entity.BlockedTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BlockedTimeRepository extends JpaRepository<BlockedTime, Long> {

    List<BlockedTime> findByPtProfile_IdOrderByStartAt(Long ptProfileId);

    List<BlockedTime> findByGymBranch_IdOrderByStartAt(Long branchId);

    /** Chồng lấn khoảng [start, end) của PT (UC-030). */
    List<BlockedTime> findByPtProfile_IdAndStartAtLessThanAndEndAtGreaterThan(
            Long ptProfileId, LocalDateTime end, LocalDateTime start);

    /** Chồng lấn khoảng [start, end) của chi nhánh (UC-030). */
    List<BlockedTime> findByGymBranch_IdAndStartAtLessThanAndEndAtGreaterThan(
            Long branchId, LocalDateTime end, LocalDateTime start);

    Optional<BlockedTime> findByIdAndPtProfile_User_Username(Long id, String username);

    /** Blocked time thuộc phạm vi quản lý của Gym (PT của Gym hoặc branch của Gym). */
    Optional<BlockedTime> findByIdAndPtProfile_GymProfile_User_Username(Long id, String username);

    Optional<BlockedTime> findByIdAndGymBranch_GymProfile_User_Username(Long id, String username);
}
