package com.fitmatch.common.enums;

/**
 * Trạng thái đơn thanh toán booking (UC-052..054).
 */
public enum PaymentStatus {

    /** Đã tạo đơn, chờ khách chuyển khoản (VietQR). */
    PENDING,

    /** Casso đối soát khớp — tiền đã vào, giữ ở ví nền tảng. */
    PAID,

    /** Thất bại. */
    FAILED,

    /** Hết hạn chờ thanh toán. */
    EXPIRED,

    /** Huỷ (booking bị huỷ trước khi thanh toán). */
    CANCELLED
}
