package com.fitmatch.repository;

import com.fitmatch.common.enums.RefundStatus;
import com.fitmatch.entity.RefundRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RefundRequestRepository extends JpaRepository<RefundRequest, Long> {

    Page<RefundRequest> findByStatus(RefundStatus status, Pageable pageable);

    /** Chặn tạo trùng yêu cầu hoàn tiền đang mở cho cùng booking (UC-055). */
    boolean existsByBooking_IdAndStatus(Long bookingId, RefundStatus status);

    Page<RefundRequest> findByBooking_Customer_Username(String username, Pageable pageable);
}
