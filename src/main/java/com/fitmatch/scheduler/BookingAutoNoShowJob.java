package com.fitmatch.scheduler;

import com.fitmatch.service.support.BookingAutoNoShowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * UC-043 (BE-3): quét booking CONFIRMED quá hạn chưa check-in -> NO_SHOW tự động.
 * Mỗi booking một transaction riêng (mẫu SettlementReleaseJob); markOneNoShow
 * recheck trạng thái nên chạy trùng an toàn. Tắt bằng app.booking.auto-no-show-enabled=false.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = "app.booking.auto-no-show-enabled", havingValue = "true", matchIfMissing = true)
public class BookingAutoNoShowJob {

    private final BookingAutoNoShowService autoNoShowService;

    @Scheduled(fixedDelayString = "${app.booking.auto-no-show-delay-ms:900000}")
    public void markOverdueNoShows() {
        try {
            for (Long bookingId : autoNoShowService.findOverdue()) {
                try {
                    autoNoShowService.markOneNoShow(bookingId);
                } catch (Exception e) {
                    log.error("Auto no-show failed for booking {}", bookingId, e);
                }
            }
        } catch (Exception e) {
            log.error("Auto no-show job failed", e);
        }
    }
}
