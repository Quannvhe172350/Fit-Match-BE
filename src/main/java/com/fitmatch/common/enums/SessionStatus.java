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
    CANCELLED,

    /**
     * Khách tự huỷ đúng ngày này (V94) và đã nhận hoàn theo mốc báo trước.
     *
     * <p>Phải TÁCH khỏi {@link #CANCELLED}: ngày bị huỷ kéo theo vé thì vé cũng
     * chết nên trả lại ngày cho vé là vô hại, còn ngày khách tự huỷ thì vé vẫn
     * sống và khách đã cầm tiền của ngày đó — trả lại ngày nữa là trả hai lần
     * cho một ngày. Mọi phép đếm "ngày đã tiêu" đều viết dưới dạng
     * {@code status <> CANCELLED} nên trạng thái riêng này tự động được tính là
     * ĐÃ TIÊU, không phải sửa từng chỗ đếm.
     */
    CANCELLED_BY_CUSTOMER
}
