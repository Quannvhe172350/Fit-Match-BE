package com.fitmatch.service;

import com.fitmatch.entity.Booking;

import java.math.BigDecimal;
import java.util.List;

/**
 * Điều phối dòng tiền escrow theo vòng đời booking (UC-057..059).
 * Trạng thái tiền lưu trên Booking.settlementStatus; bút toán do WalletService ghi.
 */
public interface SettlementService {

    /** UC-057: tiền booking vừa được giữ vào ví — đánh dấu HELD. */
    void markHeld(Booking booking);

    /**
     * UC-058: buổi tập hoàn tất (hoặc no-show mất phí) — chuyển phần held của
     * booking sang pending settlement và bắt đầu holding period. Booking miễn
     * phí/không giữ tiền thì bỏ qua.
     */
    void settleAfterFulfillment(Booking booking, String reason);

    /** UC-059: id các booking PENDING_RELEASE đã hết holding period. */
    List<Long> findDueForRelease();

    /**
     * UC-059: giải ngân một booking đến hạn — pending -> available (trừ hoa hồng
     * theo CommissionConfig hiện hành). Idempotent theo settlementStatus.
     */
    void releaseOne(Long bookingId);

    /** Số tiền thực đang giữ cho booking (đơn PAID; fallback payableAmount). */
    BigDecimal heldAmountOf(Booking booking);
}
