package com.fitmatch.scheduler;

import com.fitmatch.service.TicketMaintenanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Câu 32: vé quá hạn -> EXPIRED, tiền tự về gym và khách không hoàn được nữa.
 * Chạy ngay sau {@link SessionCompletionJob} để vé vừa dùng hết trong đêm được
 * chốt USED_UP trước, không bị đánh nhầm thành hết hạn.
 *
 * <p>Hai bước tách riêng transaction: nhắc trước khi hết hạn không được để lỗi
 * làm hỏng việc chốt hạn.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TicketExpiryJob {

    private final TicketMaintenanceService maintenanceService;

    @Scheduled(cron = "${app.ticket.expiry-cron:0 30 0 * * *}")
    public void expireOverdueTickets() {
        try {
            maintenanceService.expireOverdueTickets();
        } catch (Exception e) {
            log.error("Ticket expiry job failed", e);
        }
        try {
            maintenanceService.notifyExpiringSoon();
        } catch (Exception e) {
            log.error("Ticket expiry warning failed", e);
        }
    }
}
