package com.fitmatch.common.enums;

/**
 * Nguồn gốc một dòng phân ca. Cần phân biệt để xoá theo lô ("bỏ ca tối T2/T4/T6
 * tháng 9") không cuốn theo những ngày Gym đã thêm tay riêng lẻ.
 */
public enum ShiftSource {

    /** Sinh từ thao tác xếp lặp: ca + các thứ trong tuần + khoảng ngày. */
    RECURRING,

    /** Gym thêm lẻ đúng một ngày. */
    MANUAL
}
