package com.fitmatch.scheduler;

import com.fitmatch.service.TicketMaintenanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Câu 9: buổi tập tiêu theo NGÀY, không theo điểm danh. Ngày đã trôi qua thì
 * buổi chuyển DONE bất kể khách có mặt hay không — đây là lý do
 * BookingAutoNoShowJob biến mất hoàn toàn ở mô hình vé.
 *
 * <p>Chạy sau nửa đêm để chỉ xử lý ngày đã kết thúc trọn vẹn.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionCompletionJob {

    private final TicketMaintenanceService maintenanceService;

    @Scheduled(cron = "${app.ticket.session-completion-cron:0 10 0 * * *}")
    public void completeElapsedSessions() {
        try {
            maintenanceService.completeElapsedSessions();
        } catch (Exception e) {
            log.error("Session completion job failed", e);
        }
    }
}
