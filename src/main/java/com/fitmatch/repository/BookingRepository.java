package com.fitmatch.repository;

import com.fitmatch.common.enums.BookingStatus;
import com.fitmatch.common.enums.SettlementStatus;
import com.fitmatch.entity.Booking;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long>, JpaSpecificationExecutor<Booking> {

    Optional<Booking> findByIdAndCustomer_Username(Long id, String username);

    Optional<Booking> findByIdAndGymProfile_User_Username(Long id, String username);

    Page<Booking> findByCustomer_Username(String username, Pageable pageable);

    Page<Booking> findByCustomer_UsernameAndStatus(String username, BookingStatus status, Pageable pageable);

    Page<Booking> findByGymProfile_User_Username(String username, Pageable pageable);

    Page<Booking> findByGymProfile_User_UsernameAndStatus(String username, BookingStatus status, Pageable pageable);

    /** UC-059: booking đã hết holding period, đến hạn giải ngân về Gym. */
    List<Booking> findBySettlementStatusAndSettlementPendingAtBefore(
            SettlementStatus settlementStatus, LocalDateTime cutoff);

    /**
     * UC-043 (BE-3, audit 2026-07-17): booking CONFIRMED quá giờ + grace mà khách
     * chưa check-in và gym quên thao tác — job auto no-show xử lý để tiền không
     * treo ở held vô hạn.
     */
    List<Booking> findByStatusAndEndAtBeforeAndCheckedInAtIsNull(
            BookingStatus status, LocalDateTime cutoff);

    /** UC-041: đếm giữ chỗ trùng khung giờ, loại trừ chính booking đang dời lịch. */
    long countByGymBranch_IdAndStatusInAndStartAtLessThanAndEndAtGreaterThanAndIdNot(
            Long branchId, Collection<BookingStatus> statuses,
            LocalDateTime end, LocalDateTime start, Long excludeId);

    /** UC-049: số buổi đang giữ chỗ của một gói đã mua (chưa hoàn tất) — chống đặt vượt số buổi còn lại. */
    long countByCustomerPackage_IdAndStatusInAndIdNot(
            Long customerPackageId, Collection<BookingStatus> statuses, Long excludeId);

    /** UC-076: đếm booking theo trạng thái trong khoảng thời gian; gymId null = toàn nền tảng. */
    @org.springframework.data.jpa.repository.Query(
            "select b.status, count(b) from Booking b "
            + "where b.createdAt >= :from and b.createdAt < :to "
            + "and (:gymId is null or b.gymProfile.id = :gymId) group by b.status")
    java.util.List<Object[]> countByStatusInRange(
            @org.springframework.data.repository.query.Param("from") LocalDateTime from,
            @org.springframework.data.repository.query.Param("to") LocalDateTime to,
            @org.springframework.data.repository.query.Param("gymId") Long gymId);

    /** UC-033: booking đang giữ chỗ của PT chồng lấn [start, end) — chống double-booking. */
    List<Booking> findByPtProfile_IdAndStatusInAndStartAtLessThanAndEndAtGreaterThan(
            Long ptId, Collection<BookingStatus> statuses, LocalDateTime end, LocalDateTime start);

    /** UC-033: đếm booking giữ chỗ tại chi nhánh trong khung giờ — kiểm tra capacity. */
    long countByGymBranch_IdAndStatusInAndStartAtLessThanAndEndAtGreaterThan(
            Long branchId, Collection<BookingStatus> statuses, LocalDateTime end, LocalDateTime start);

    /** UC-045: lịch của PT đang đăng nhập. */
    Page<Booking> findByPtProfile_User_Username(String username, Pageable pageable);

    Page<Booking> findByPtProfile_User_UsernameAndStatus(String username, BookingStatus status, Pageable pageable);

    /** UC-045: admin xem toàn hệ thống theo trạng thái. */
    Page<Booking> findByStatus(BookingStatus status, Pageable pageable);

    /** P1-16/17: đếm booking đang giữ chỗ trong tương lai của một chi nhánh. */
    long countByGymBranch_IdAndStatusInAndStartAtGreaterThan(
            Long branchId, Collection<BookingStatus> statuses, LocalDateTime after);

    /** P1-16: đếm booking đang giữ chỗ trong tương lai của một PT. */
    long countByPtProfile_IdAndStatusInAndStartAtGreaterThan(
            Long ptId, Collection<BookingStatus> statuses, LocalDateTime after);

    /** P1-15 (C-2): booking giữ chỗ tương lai của một PT — kiểm tra khi thu hẹp lịch rảnh. */
    List<Booking> findByPtProfile_IdAndStatusInAndStartAtGreaterThan(
            Long ptId, Collection<BookingStatus> statuses, LocalDateTime after);

    /** P1-15 (C-1): booking giữ chỗ tương lai của một chi nhánh — kiểm tra khi thu hẹp giờ mở cửa. */
    List<Booking> findByGymBranch_IdAndStatusInAndStartAtGreaterThan(
            Long branchId, Collection<BookingStatus> statuses, LocalDateTime after);

    /** P1-18 (UC-023): đếm booking của một PT theo trạng thái (monitor performance). */
    long countByPtProfile_IdAndStatus(Long ptId, BookingStatus status);
}
