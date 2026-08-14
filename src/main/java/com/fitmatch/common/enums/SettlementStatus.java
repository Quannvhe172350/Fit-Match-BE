package com.fitmatch.common.enums;

/**
 * Trạng thái dòng tiền escrow của một VÉ (UC-057..059, UC-055/056).
 * Chạy song song với TicketStatus: vé mô tả quyền sử dụng, settlement mô tả tiền.
 */
public enum SettlementStatus {

    /** Không có tiền giữ (điểm/voucher phủ hết, hoặc chưa thanh toán). */
    NONE,

    /** Tiền đã vào bucket held của ví Gym (UC-057). */
    HELD,

    /** Đang chờ yêu cầu hoàn tiền được duyệt (UC-055) — chặn release. */
    REFUND_PENDING,

    /** Vé dùng hết / hết hạn — tiền ở pending, chờ hết holding period (UC-058). */
    PENDING_RELEASE,

    /** Đang tranh chấp — tiền kéo về held, chặn auto-release tới khi giải quyết (UC-063/067). */
    DISPUTED,

    /** Đã giải ngân về available của Gym, trừ hoa hồng (UC-059). */
    RELEASED,

    /** Đã hoàn toàn bộ phần giữ cho khách (UC-056). */
    REFUNDED
}
