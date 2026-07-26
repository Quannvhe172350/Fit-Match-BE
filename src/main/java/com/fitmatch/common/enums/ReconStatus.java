package com.fitmatch.common.enums;

/**
 * Trạng thái đối soát của một giao dịch ngân hàng nhận từ Casso (UC-053/056).
 * Tiền đã vào tài khoản nền tảng nhưng không chắc gắn được vào booking nào —
 * mọi trường hợp bất thường phải nằm ở {@link #NEEDS_REVIEW} để Finance xử lý,
 * thay vì chỉ ghi log rồi trôi mất.
 */
public enum ReconStatus {

    /** Khớp refCode + đủ tiền, webhook đã tự áp vào booking. Không cần can thiệp. */
    APPLIED,

    /** Bất thường (xem {@code anomaly}) — đang chờ Finance/Admin quyết định. */
    NEEDS_REVIEW,

    /** Finance đã gắn tay giao dịch vào một booking và ghi nhận giữ tiền. */
    RESOLVED_APPLIED,

    /** Finance đã chuyển trả lại người gửi (thao tác ngân hàng ngoài hệ thống). */
    RESOLVED_REFUNDED,

    /** Finance xác định không cần hành động (tiền không thuộc luồng booking, trùng, test...). */
    RESOLVED_IGNORED
}
