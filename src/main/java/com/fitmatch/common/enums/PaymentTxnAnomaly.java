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
    DUPLICATE
}
