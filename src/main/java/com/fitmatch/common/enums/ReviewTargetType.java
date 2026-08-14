package com.fitmatch.common.enums;

/**
 * Câu 17 + 36: đánh giá phòng gym và đánh giá PT là hai thứ khác nhau, mở ra ở
 * hai thời điểm khác nhau và neo vào hai thực thể khác nhau.
 */
public enum ReviewTargetType {

    /** Đánh giá phòng gym — neo vào VÉ, mở khi vé đã dùng hết (USED_UP). */
    GYM,

    /** Đánh giá PT — neo vào BUỔI TẬP, mở khi buổi đó xong (DONE) và có PT. */
    PT
}
