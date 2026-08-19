package com.fitmatch.common.enums;

/**
 * Phạm vi giờ giấc mà đơn nghỉ phủ, áp cho MỌI ngày trong [fromDate, toDate].
 */
public enum LeaveScope {

    /** Nghỉ trọn ngày — phủ mọi ca của PT trong ngày đó. */
    FULL_DAY,

    /** Nghỉ một hoặc nhiều ca cụ thể; danh sách ca nằm ở pt_leave_request_shifts. */
    SHIFT,

    /** Nghỉ một khoảng giờ tự do — dùng startTime/endTime của đơn. */
    TIME_RANGE
}
