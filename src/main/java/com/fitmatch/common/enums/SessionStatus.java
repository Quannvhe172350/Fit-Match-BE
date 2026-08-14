package com.fitmatch.common.enums;

/**
 * Vòng đời của một ngày tập. Cố ý rất hẹp: không có PENDING_GYM (gym không
 * duyệt lịch nữa) và không có NO_SHOW (câu 9 — buổi tiêu theo ngày, bất kể
 * khách có mặt hay không).
 *
 * <p>Dời lịch, đổi khung giờ PT, thêm/bỏ PT đều KHÔNG đổi trạng thái — chỉ
 * sửa tại chỗ và ghi một dòng session_status_history qua
 * {@code SessionLifecycle.recordNote}.
 */
public enum SessionStatus {

    /** Đã đặt, chưa tới ngày (hoặc đúng ngày hôm nay). */
    SCHEDULED,

    /** Ngày tập đã trôi qua — do SessionCompletionJob chuyển, không do check-in. */
    DONE,

    /** Bị huỷ kéo theo khi vé bị huỷ / hoàn tiền. */
    CANCELLED
}
