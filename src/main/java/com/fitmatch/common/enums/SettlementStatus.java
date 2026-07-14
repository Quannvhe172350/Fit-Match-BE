package com.fitmatch.common.enums;

/**
 * Trạng thái dòng tiền escrow của một booking (UC-057..059, UC-055/056).
 * Chạy song song với BookingStatus: booking mô tả dịch vụ, settlement mô tả tiền.
 */
public enum SettlementStatus {

    /** Không có tiền giữ (booking miễn phí hoặc chưa thanh toán). */
    NONE,

    /** Tiền đã vào bucket held của ví Gym (UC-057). */
    HELD,

    /** Đang chờ yêu cầu hoàn tiền được duyệt (UC-055) — chặn release. */
    REFUND_PENDING,

    /** Buổi tập hoàn tất / no-show — tiền ở pending, chờ hết holding period (UC-058). */
    PENDING_RELEASE,

    /** Đã giải ngân về available của Gym, trừ hoa hồng (UC-059). */
    RELEASED,

    /** Đã hoàn toàn bộ phần giữ cho khách (UC-056). */
    REFUNDED
}
