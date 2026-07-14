package com.fitmatch.scheduler;

import com.fitmatch.service.SettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * UC-059: booking PENDING_RELEASE hết holding period -> giải ngân về available
 * của Gym (trừ hoa hồng). Mỗi booking một transaction riêng để một lỗi không
 * chặn cả lô; releaseOne idempotent nên chạy trùng an toàn.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementReleaseJob {

    private final SettlementService settlementService;

    @Scheduled(fixedDelayString = "${app.settlement.release-job-delay-ms:900000}")
    public void releaseDueSettlements() {
        try {
            for (Long bookingId : settlementService.findDueForRelease()) {
                try {
                    settlementService.releaseOne(bookingId);
                } catch (Exception e) {
                    log.error("Settlement release failed for booking {}", bookingId, e);
                }
            }
        } catch (Exception e) {
            log.error("Settlement release job failed", e);
        }
    }
}
