package com.fitmatch.common.enums;

/**
 * Loại đơn nghỉ của PT. Chỉ để phân loại và thống kê — KHÔNG loại nào được
 * miễn duyệt: quyết định §4.2 nói Gym là chủ lịch, PT tự khoá slot được thì
 * ràng buộc bảo vệ buổi khách đã đặt (§4.1) mất nghĩa.
 */
public enum LeaveType {

    /** Nghỉ phép có kế hoạch. */
    LEAVE,

    /** Ốm — thường kèm đơn thuốc/giấy nghỉ ở attachmentUrl. */
    SICK,

    /** Bận việc riêng. */
    BUSY,

    /** Lý do khác — bắt buộc mô tả rõ trong reason. */
    OTHER
}
