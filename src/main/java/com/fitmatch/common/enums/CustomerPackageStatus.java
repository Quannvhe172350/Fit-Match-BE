package com.fitmatch.common.enums;

/** Trạng thái gói tập khách đã mua (UC-049/051). */
public enum CustomerPackageStatus {

    /** Còn buổi và còn hạn — đặt được buổi tập mới. */
    ACTIVE,

    /** Đã dùng hết số buổi. */
    EXHAUSTED,

    /** Quá hạn sử dụng (validityDays). */
    EXPIRED
}
