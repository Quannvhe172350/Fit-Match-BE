package com.fitmatch.repository;

import com.fitmatch.common.enums.LeaveStatus;
import com.fitmatch.entity.PtLeaveRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PtLeaveRequestRepository extends JpaRepository<PtLeaveRequest, Long> {

    /**
     * PtSlotValidator + kiểm chồng đơn: đơn của PT còn hiệu lực và GIAO với
     * khoảng ngày đang hỏi. Điều kiện giao nhau chuẩn: from <= to' && to >= from'.
     */
    @Query("select r from PtLeaveRequest r where r.ptProfile.id = :ptId "
            + "and r.status in :statuses and r.fromDate <= :to and r.toDate >= :from")
    List<PtLeaveRequest> findOverlapping(@Param("ptId") Long ptProfileId,
                                         @Param("statuses") Collection<LeaveStatus> statuses,
                                         @Param("from") LocalDate from,
                                         @Param("to") LocalDate to);

    /** Bản nhiều PT một lượt — lưới chọn PT của khách không được hỏi từng PT một. */
    @Query("select r from PtLeaveRequest r where r.ptProfile.id in :ptIds "
            + "and r.status = :status and r.fromDate <= :to and r.toDate >= :from")
    List<PtLeaveRequest> findOverlappingForPts(@Param("ptIds") Collection<Long> ptProfileIds,
                                               @Param("status") LeaveStatus status,
                                               @Param("from") LocalDate from,
                                               @Param("to") LocalDate to);

    /** Hạn mức tháng (§4.2): đếm đơn còn hiệu lực có fromDate rơi trong tháng. */
    @Query("select count(r) from PtLeaveRequest r where r.ptProfile.id = :ptId "
            + "and r.status in :statuses and r.fromDate between :monthStart and :monthEnd")
    long countInMonth(@Param("ptId") Long ptProfileId,
                      @Param("statuses") Collection<LeaveStatus> statuses,
                      @Param("monthStart") LocalDate monthStart,
                      @Param("monthEnd") LocalDate monthEnd);

    Page<PtLeaveRequest> findByPtProfile_User_UsernameOrderByCreatedAtDesc(
            String username, Pageable pageable);

    Page<PtLeaveRequest> findByGymProfile_User_UsernameOrderByCreatedAtDesc(
            String username, Pageable pageable);

    Page<PtLeaveRequest> findByGymProfile_User_UsernameAndStatusOrderByCreatedAtDesc(
            String username, LeaveStatus status, Pageable pageable);

    Optional<PtLeaveRequest> findByIdAndGymProfile_User_Username(Long id, String username);

    Optional<PtLeaveRequest> findByIdAndPtProfile_User_Username(Long id, String username);

    /** Badge số đơn chờ duyệt trên sidebar của Gym. */
    long countByGymProfile_User_UsernameAndStatus(String username, LeaveStatus status);
}
