package com.fitmatch.common.enums;

/**
 * Vòng đời booking (UC-040). Chuyển trạng thái hợp lệ được kiểm soát tập trung
 * tại BookingLifecycleService.
 */
public enum BookingStatus {

    /** Customer đang soạn yêu cầu (UC-031/032) — chưa gửi. */
    DRAFT,

    /** Đã checkout, chờ thanh toán giữ tiền (UC-035/036 — gateway ở Phase payment). */
    PENDING_PAYMENT,

    /** Tiền đã giữ (hoặc miễn phí) — chuyển Gym xử lý (UC-037). */
    PENDING_GYM,

    /** Gym đã nhận (UC-038), PT/nguồn lực đã gán (UC-039). */
    CONFIRMED,

    /** Gym từ chối (UC-038) — kích hoạt hoàn tiền ở phase payment. */
    REJECTED,

    /** Hủy bởi customer/gym (UC-042) — phí/hoàn tiền theo chính sách. */
    CANCELLED,

    /** Khách không đến (UC-043). */
    NO_SHOW,

    /** Buổi tập hoàn tất (UC-049 — attendance phase sẽ kích hoạt). */
    COMPLETED
}
