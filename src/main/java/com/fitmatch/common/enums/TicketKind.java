package com.fitmatch.common.enums;

/**
 * Loại vé trong catalog của gym (thay gym_services / training_packages).
 */
public enum TicketKind {

    /** Vé một ngày — day_count luôn = 1, khách tự chọn ngày và được đổi ngày. */
    DAY,

    /** Vé gói n ngày LIÊN TIẾP kể từ ngày bắt đầu — không đổi lịch từng ngày. */
    PACKAGE
}
