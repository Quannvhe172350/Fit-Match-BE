package com.fitmatch.common.enums;

/**
 * Lý do một giao dịch ngân hàng không đi trọn được luồng tự động (UC-053).
 * Null = giao dịch bình thường (khớp và đã áp vào booking).
 */
public enum PaymentTxnAnomaly {

    /** Nội dung chuyển khoản không chứa refCode nào của hệ thống. */
    UNMATCHED,

    /** Khớp đơn nhưng chuyển thiếu tiền — chưa đủ điều kiện xác nhận booking. */
    UNDERPAID,

    /** Khớp đơn và đã xác nhận, nhưng chuyển thừa — phần thừa cần trả lại khách. */
    OVERPAID,

    /** Tiền vào sau khi đơn thanh toán đã hết hạn (booking đã bị hủy). */
    LATE_ARRIVAL,

    /** Đơn không còn ở trạng thái chờ (đã thanh toán/hủy) — thường là chuyển khoản trùng. */
    DUPLICATE,

    /**
     * V61 — giao dịch CHI trên sao kê nhưng nội dung không chứa mã lệnh rút nào.
     * Tiền đã rời tài khoản nền tảng mà không gắn được với lệnh rút: phải soi tay.
     */
    PAYOUT_UNMATCHED,

    /** V61 — khớp lệnh rút nhưng số tiền chi khác số tiền được duyệt. */
    PAYOUT_AMOUNT_MISMATCH,

    /** V61 — khớp lệnh rút nhưng lệnh không ở trạng thái APPROVED (chưa duyệt/đã chi rồi). */
    PAYOUT_STATE_MISMATCH
}
