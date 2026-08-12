package com.fitmatch.scheduler;

import com.fitmatch.config.GeocodingProperties;
import com.fitmatch.repository.GeocodeCacheRepository;
import com.fitmatch.service.GeocodingBackfillService;
import com.fitmatch.service.GeocodingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * V59 (UC-18): giữ cho dữ liệu toạ độ không bị bỏ quên.
 *
 * <p>Hai việc mà trước đây chỉ chạy khi Admin nhớ ra và bấm tay:
 * <ol>
 *   <li>Hồ sơ geocode hỏng lúc lưu (Google timeout, hết quota) nằm im vô hình
 *       trong tìm kiếm quanh đây cho tới khi có người phát hiện.</li>
 *   <li>Toạ độ geocode từ lâu không bao giờ được làm mới.</li>
 * </ol>
 *
 * <p>Mặc định TẮT ({@code app.geocoding.backfill-job-enabled=false}): job này
 * tiêu quota Google mà không có ai nhìn, nên phải là quyết định có ý thức của
 * người vận hành. Khi chạy nhiều instance, cần thêm khoá phân tán — hiện mỗi
 * instance sẽ tự chạy một bản.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeocodingBackfillJob {

    private static final String ACTOR = "scheduler";

    private final GeocodingBackfillService geocodingBackfillService;
    private final GeocodingService geocodingService;
    private final GeocodeCacheRepository geocodeCacheRepository;
    private final GeocodingProperties properties;

    @Scheduled(cron = "${app.geocoding.backfill-job-cron:0 30 3 * * *}")
    public void run() {
        if (!properties.isBackfillJobEnabled() || !geocodingService.isEnabled()) {
            return;
        }
        int limit = properties.getBackfillJobLimit();
        // Hai pha tách nhau và bắt lỗi riêng: pha làm mới hỏng không được cản pha
        // bổ sung, vì bổ sung mới là thứ quyết định gym có xuất hiện trong tìm
        // kiếm hay không.
        try {
            var result = geocodingBackfillService.backfill(limit, ACTOR);
            if (result.gymsUpdated() > 0 || result.branchesUpdated() > 0 || result.remaining() > 0) {
                log.info("Geocoding backfill job: gyms {}/{}, branches {}/{}, còn lại {}",
                        result.gymsUpdated(), result.gymsScanned(),
                        result.branchesUpdated(), result.branchesScanned(), result.remaining());
            }
        } catch (Exception e) {
            log.error("Geocoding backfill job thất bại", e);
        }
        try {
            geocodingBackfillService.refreshStale(limit);
        } catch (Exception e) {
            log.error("Geocoding refresh job thất bại", e);
        }
        purgeExpiredCache();
    }

    /**
     * Dọn bản ghi đệm quá hạn. Đệm hết hạn vẫn bị bỏ qua khi đọc (lọc theo
     * {@code cachedAt}), nên đây thuần tuý là giữ cho bảng khỏi phình.
     */
    private void purgeExpiredCache() {
        try {
            LocalDateTime expiredBefore = LocalDateTime.now().minusDays(properties.getCacheTtlDays());
            int purged = geocodeCacheRepository.deleteByCachedAtBefore(expiredBefore);
            if (purged > 0) {
                log.info("Đã dọn {} bản ghi geocode cache quá hạn", purged);
            }
        } catch (Exception e) {
            log.warn("Không dọn được geocode cache: {}", e.getMessage());
        }
    }
}
