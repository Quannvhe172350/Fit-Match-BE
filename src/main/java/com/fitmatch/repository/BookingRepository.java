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

    /** UC-041: đếm giữ chỗ trùng khung giờ, loại trừ chính booking đang dời lịch. */
    long countByGymBranch_IdAndStatusInAndStartAtLessThanAndEndAtGreaterThanAndIdNot(
            Long branchId, Collection<BookingStatus> statuses,
            LocalDateTime end, LocalDateTime start, Long excludeId);

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
}
