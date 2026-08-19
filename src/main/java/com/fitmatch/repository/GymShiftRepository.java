package com.fitmatch.repository;

import com.fitmatch.entity.GymShift;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface GymShiftRepository extends JpaRepository<GymShift, Long> {

    List<GymShift> findByGymBranch_IdOrderByStartTimeAsc(Long branchId);

    /** Lưới phân ca và sinh slot cho khách chỉ quan tâm ca đang bật. */
    List<GymShift> findByGymBranch_IdAndActiveTrueOrderByStartTimeAsc(Long branchId);

    /** Kiểm sở hữu: ca phải thuộc chi nhánh của đúng Gym đang đăng nhập. */
    Optional<GymShift> findByIdAndGymBranch_GymProfile_User_Username(Long id, String username);

    List<GymShift> findByIdInAndGymBranch_GymProfile_User_Username(
            Collection<Long> ids, String username);

    /** Mọi ca đang bật của các chi nhánh mà PT được phân công — dùng khi PT chọn ca để xin nghỉ. */
    @Query("select s from GymShift s where s.active = true and s.gymBranch.id in "
            + "(select a.gymBranch.id from PtAssignment a where a.ptProfile.id = :ptId and a.active = true) "
            + "order by s.gymBranch.id, s.startTime")
    List<GymShift> findActiveShiftsOfPtBranches(@Param("ptId") Long ptProfileId);

    boolean existsByGymBranch_IdAndNameAndIdNot(Long branchId, String name, Long excludeId);
}
