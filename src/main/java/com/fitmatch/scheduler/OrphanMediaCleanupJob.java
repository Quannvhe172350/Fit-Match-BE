package com.fitmatch.scheduler;

import com.fitmatch.config.MediaProperties;
import com.fitmatch.entity.MediaAsset;
import com.fitmatch.repository.MediaAssetRepository;
import com.fitmatch.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Dọn ảnh nháp mồ côi: người dùng chọn ảnh cho đánh giá rồi đóng tab, ảnh đã nằm
 * trên GCS nhưng không bao giờ được gắn vào bản ghi nào. Không dọn thì hoá đơn
 * lưu trữ cứ tăng vì những file không màn hình nào đọc tới.
 *
 * <p>Chỉ đụng tới bản ghi có {@code entity_id IS NULL} và đã quá
 * {@code app.media.orphan-retention-hours} — ảnh vừa upload của form đang mở
 * tuyệt đối an toàn. Tắt bằng {@code app.media.cleanup-job-enabled=false}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = "app.media.cleanup-job-enabled", havingValue = "true", matchIfMissing = true)
public class OrphanMediaCleanupJob {

    private static final int BATCH_SIZE = 200;

    private final MediaAssetRepository mediaRepository;
    private final StorageService storageService;
    private final MediaProperties properties;

    @Scheduled(cron = "${app.media.cleanup-job-cron:0 15 3 * * *}")
    @Transactional
    public void removeOrphanedDrafts() {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusHours(properties.getOrphanRetentionHours());
            List<MediaAsset> orphans = mediaRepository
                    .findByEntityIdIsNullAndCreatedAtBefore(cutoff, PageRequest.of(0, BATCH_SIZE));
            if (orphans.isEmpty()) return;

            mediaRepository.deleteAll(orphans);
            for (MediaAsset media : orphans) {
                storageService.delete(media.getStorageKey());
                if (media.getThumbnailKey() != null) storageService.delete(media.getThumbnailKey());
            }
            log.info("Cleaned up {} orphaned draft images", orphans.size());
        } catch (Exception e) {
            log.error("Orphan media cleanup failed", e);
        }
    }
}
