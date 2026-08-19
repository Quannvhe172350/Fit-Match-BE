package com.fitmatch.common.enums;

/**
 * Nhóm thông báo (UC-075). MARKETING bị chặn nếu user tắt marketingEnabled;
 * các nhóm giao dịch (booking/payment/...) luôn được lưu vào hộp thư in-app.
 */
public enum NotificationCategory {
    BOOKING,
    PAYMENT,
    SETTLEMENT,
    DISPUTE,
    REVIEW,
    ACCOUNT,
    /** Vận hành nhân sự của Gym: đơn nghỉ của PT, phân ca, cảnh báo chưa xếp ca. */
    WORKFORCE,
    SYSTEM,
    MARKETING
}
