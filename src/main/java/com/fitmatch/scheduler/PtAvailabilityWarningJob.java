package com.fitmatch.scheduler;

import com.fitmatch.service.TicketMaintenanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Quyết định #8: PT khai dưới ngưỡng ngày thì được nhắc, KHÔNG bị chặn. Job này
 * chỉ gửi thông báo — không đụng tới trạng thái PT và không ảnh hưởng kết quả
 * tìm kiếm của khách.
 *
 * <p>Chạy hàng tuần chứ không hàng ngày: nhắc mỗi ngày là spam, và PT cần vài
 * ngày để thực sự ngồi khai lịch.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PtAvailabilityWarningJob {

    private final TicketMaintenanceService maintenanceService;

    @Scheduled(cron = "${app.pt.availability-warning-cron:0 0 9 * * MON}")
    public void warnThinAvailability() {
        try {
            int warned = maintenanceService.warnPtsWithThinAvailability();
            if (warned > 0) {
                log.info("Warned {} PT(s) about thin availability", warned);
            }
        } catch (Exception e) {
            log.error("PT availability warning job failed", e);
        }
    }
}
