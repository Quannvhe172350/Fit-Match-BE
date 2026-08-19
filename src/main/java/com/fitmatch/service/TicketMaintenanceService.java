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
     * V85: PT đang ACTIVE nhưng GYM chưa xếp ca nào trong thời gian tới -> cảnh
     * báo cho Gym. Thay cảnh báo "PT khai dưới 20 ngày" của mô hình cũ: PT
     * không còn quyền khai lịch, nên lịch mỏng giờ là việc Gym chưa làm chứ
     * không phải lỗi của PT. Chỉ là thông báo, KHÔNG ẩn PT khỏi tìm kiếm.
     *
     * @return số PT bị cảnh báo
     */
    int warnPtsWithoutRoster();

    /**
     * Quyết định §4.1: buổi tập mất PT mà tới ngày khách vẫn chưa chọn PT thay
     * thế -> tự hoàn phụ phí PT. Khách không được thiệt vì quên thao tác.
     *
     * @return số quyết định đã chốt
     */
    int autoResolvePtCancellations();
}
