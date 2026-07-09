package com.fitmatch.repository;

import com.fitmatch.common.enums.PaymentStatus;
import com.fitmatch.entity.PaymentOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {

    Optional<PaymentOrder> findByRefCode(String refCode);

    Optional<PaymentOrder> findByBooking_Id(Long bookingId);

    Optional<PaymentOrder> findByBooking_IdAndBooking_Customer_Username(Long bookingId, String username);

    /** UC-054: đơn PENDING đã quá hạn — dùng cho job hết hạn thanh toán. */
    List<PaymentOrder> findByStatusAndExpiresAtBefore(PaymentStatus status, LocalDateTime cutoff);
}
