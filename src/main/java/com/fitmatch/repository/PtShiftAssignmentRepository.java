package com.fitmatch.repository;

import com.fitmatch.entity.PtShiftAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PtShiftAssignmentRepository extends JpaRepository<PtShiftAssignment, Long> {

    /**
     * PtSlotValidator: mọi ca PT làm trong một ngày. Fetch join gym_shifts vì
     * caller luôn cần start/end/slotMinutes ngay sau đó.
     */
    @Query("select a from PtShiftAssignment a join fetch a.gymShift s "
            + "where a.ptProfile.id = :ptId and a.workDate = :date and a.active = true")
    List<PtShiftAssignment> findActiveByPtAndDate(@Param("ptId") Long ptProfileId,
                                                  @Param("date") LocalDate date);

    /** Lưới ngày x giờ cho khách: mọi ca của nhóm PT trong một chi nhánh. */
    @Query("select a from PtShiftAssignment a join fetch a.gymShift s "
            + "where a.ptProfile.id in :ptIds and s.gymBranch.id = :branchId "
            + "and a.workDate between :from and :to and a.active = true and s.active = true "
            + "order by a.workDate, s.startTime")
    List<PtShiftAssignment> findActiveByPtsAndBranchBetween(@Param("ptIds") Collection<Long> ptIds,
                                                            @Param("branchId") Long branchId,
                                                            @Param("from") LocalDate from,
                                                            @Param("to") LocalDate to);

    /** Lịch ca của một PT (màn read-only của PT, và kiểm chồng ca khi Gym xếp). */
    @Query("select a from PtShiftAssignment a join fetch a.gymShift s "
            + "where a.ptProfile.id = :ptId and a.workDate between :from and :to and a.active = true "
            + "order by a.workDate, s.startTime")
    List<PtShiftAssignment> findActiveByPtBetween(@Param("ptId") Long ptProfileId,
                                                  @Param("from") LocalDate from,
                                                  @Param("to") LocalDate to);

    /** Lưới phân ca của Gym: mọi dòng thuộc các ca của một chi nhánh. */
    @Query("select a from PtShiftAssignment a join fetch a.gymShift s join fetch a.ptProfile p "
            + "where s.gymBranch.id = :branchId and a.workDate between :from and :to "
            + "order by p.id, a.workDate, s.startTime")
    List<PtShiftAssignment> findRosterOfBranch(@Param("branchId") Long branchId,
                                               @Param("from") LocalDate from,
                                               @Param("to") LocalDate to);

    Optional<PtShiftAssignment> findByPtProfile_IdAndGymShift_IdAndWorkDate(
            Long ptProfileId, Long shiftId, LocalDate workDate);

    Optional<PtShiftAssignment> findByIdAndPtProfile_GymProfile_User_Username(Long id, String username);

    /** Xoá theo lô một đợt xếp lặp — chỉ đụng dòng RECURRING, không cuốn ngày Gym thêm tay. */
    List<PtShiftAssignment> findByPtProfile_IdAndGymShift_IdAndWorkDateBetween(
            Long ptProfileId, Long shiftId, LocalDate from, LocalDate to);

    /** Mọi dòng phân ca của một ca — dùng khi Gym sửa hoặc xoá chính ca đó. */
    List<PtShiftAssignment> findByGymShift_Id(Long shiftId);

    List<PtShiftAssignment> findByGymShift_IdAndWorkDateGreaterThanEqual(
            Long shiftId, LocalDate from);

    /** Edge case §7.2: ca này còn dòng phân ca nào không (chặn xoá ca). */
    long countByGymShift_IdAndWorkDateGreaterThanEqual(Long shiftId, LocalDate from);

    /** Edge case §7.3/7.4: PT còn ca tương lai không. */
    long countByPtProfile_IdAndActiveTrueAndWorkDateGreaterThanEqual(Long ptProfileId, LocalDate from);

    List<PtShiftAssignment> findByPtProfile_IdAndActiveTrueAndWorkDateGreaterThanEqual(
            Long ptProfileId, LocalDate from);

    /** Cảnh báo "Gym chưa xếp ca cho PT" — thay job đếm ngày PT tự khai. */
    long countByPtProfile_IdAndActiveTrueAndWorkDateBetween(
            Long ptProfileId, LocalDate from, LocalDate to);
}
