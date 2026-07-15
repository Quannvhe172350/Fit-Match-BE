package com.fitmatch.common.enums;

/** Kiểu giảm giá voucher (UC-073). */
public enum DiscountType {

    /** Giảm theo % giá trị booking (có thể chặn trần maxDiscount). */
    PERCENT,

    /** Giảm số tiền cố định. */
    FIXED
}
