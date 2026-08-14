package com.fitmatch.service;

/**
 * Việc chạy nền của mô hình vé. Tách khỏi các scheduler để logic nằm trong
 * transaction của service và test được mà không cần dựng Spring context.
 */
public interface TicketMaintenanceService {

    /**
     * Buổi tập đã qua ngày -> DONE (câu 9: tiêu theo ngày, không theo điểm
     * danh). Vé nào đủ số ngày thì chuyển USED_UP và giải ngân.
     *
     * @return số buổi đã hoàn tất
     */
    int completeElapsedSessions();

    /**
     * Câu 32: vé quá expires_at -> EXPIRED, tiền tự về gym và khách không hoàn
     * được nữa. Idempotent — chạy hai lần không giải ngân hai lần.
     *
     * @return số vé đã hết hạn
     */
    int expireOverdueTickets();

    /**
     * Nhắc khách trước khi vé hết hạn. Chạy cùng lịch với job hết hạn để không
     * có vé nào chết lặng lẽ.
     *
     * @return số thông báo đã gửi
     */
    int notifyExpiringSoon();

    /**
     * Quyết định #8: PT khai dưới ngưỡng ngày -> cảnh báo PT và gym quản lý.
     * Chỉ là thông báo, KHÔNG chặn PT khỏi kết quả tìm kiếm.
     *
     * @return số PT bị cảnh báo
     */
    int warnPtsWithThinAvailability();
}
