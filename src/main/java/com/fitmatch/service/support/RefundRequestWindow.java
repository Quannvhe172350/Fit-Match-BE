package com.fitmatch.service.support;

import com.fitmatch.entity.Ticket;
import com.fitmatch.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Cửa sổ thời gian khách được gửi YÊU CẦU HOÀN TIỀN (UC-055). Cùng khuôn với
 * {@link DisputeWindow} nhưng neo vào một mốc khác, vì hai luồng trả lời hai câu
 * hỏi khác nhau:
 *
 * <ul>
 *   <li>Hoàn tiền = "tôi đổi ý / tôi không tập nữa" → đếm từ {@code startDate},
 *       ngày tập đầu tiên của vé.</li>
 *   <li>Tranh chấp = "gym/PT làm sai" → đếm từ lúc vé kết toán, và chỉ đóng khi
 *       tiền đã rời hệ thống.</li>
 * </ul>
 *
 * <p>Mốc {@code startDate} khớp đúng cách {@link PartialRefundCalculator} tính
 * ngày đã dùng: vé chưa xếp lịch hoặc chưa tới ngày bắt đầu thì elapsed = 0 và
 * được hoàn 100% — nên ở đây cũng KHÔNG áp hạn, khách chưa nhận gì thì chưa có
 * gì để tính giờ. Hết hạn không phải là hết đường: nếu gym thực sự sai thì tranh
 * chấp vẫn mở được (xem thông báo lỗi ở TicketRefundServiceImpl).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefundRequestWindow {

    public static final String CONFIG_KEY = "refund.request-window-days";

    private final SystemConfigService systemConfigService;

    /** {@code <= 0} = không giới hạn. Chỉ đặt được qua env: API admin chặn số <= 0. */
    @Value("${app.refund.request-window-days:7}")
    private int defaultWindowDays;

    public int windowDays() {
        Long configured = systemConfigService.findLong(CONFIG_KEY);
        return configured != null ? configured.intValue() : defaultWindowDays;
    }

    /** Ngày cuối còn gửi được yêu cầu hoàn; null = chưa bắt đầu tính hoặc không áp hạn. */
    public LocalDate deadline(Ticket ticket) {
        int days = windowDays();
        if (days <= 0 || ticket.getStartDate() == null) {
            return null;
        }
        return ticket.getStartDate().plusDays(days);
    }

    public boolean expired(Ticket ticket) {
        LocalDate deadline = deadline(ticket);
        return deadline != null && LocalDate.now().isAfter(deadline);
    }
}
