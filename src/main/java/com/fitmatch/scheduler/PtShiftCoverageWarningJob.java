package com.fitmatch.scheduler;

import com.fitmatch.service.TicketMaintenanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * V85 thay {@code PtAvailabilityWarningJob}: mô hình cũ nhắc PT khi tự khai
 * dưới 20 ngày; giờ PT không khai lịch nữa, nên thứ đáng nhắc là GYM CHƯA XẾP
 * CA cho một PT đang hoạt động — PT đó vô hình với khách mà không ai biết.
 *
 * <p>Chỉ gửi thông báo, không đụng trạng thái PT và không ẩn PT khỏi tìm kiếm.
 * Chạy hàng tuần chứ không hàng ngày: nhắc mỗi ngày là spam, và xếp ca là việc
 * cần vài ngày để sắp xếp.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PtShiftCoverageWarningJob {

    private final TicketMaintenanceService maintenanceService;

    @Scheduled(cron = "${app.pt.shift-coverage-warning-cron:0 0 9 * * MON}")
    public void warnPtsWithoutRoster() {
        try {
            int warned = maintenanceService.warnPtsWithoutRoster();
            if (warned > 0) {
                log.info("Warned gyms about {} PT(s) without any upcoming shift", warned);
            }
        } catch (Exception e) {
            log.error("PT shift coverage warning job failed", e);
        }
    }
}
