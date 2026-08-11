package com.fitmatch.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Tham số của hệ thống ảnh ({@code app.media.*}). Không hằng số nào ở đây được
 * hard-code trong service — giới hạn dung lượng/số ảnh khác nhau giữa dev và prod.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.media")
public class MediaProperties {

    /** Dung lượng tối đa mỗi ảnh (MB). */
    private int maxImageSizeMb = 5;

    /** Số ảnh tối đa cho một entity ở mỗi loại gallery. */
    private int maxImagesPerEntity = 10;

    /** Số file tối đa trong một request upload. */
    private int maxFilesPerRequest = 10;

    /** MIME được chấp nhận — kiểm tra bằng magic bytes chứ không tin Content-Type. */
    private List<String> allowedMimeTypes =
            List.of("image/jpeg", "image/png", "image/webp");

    /**
     * true = bucket cho đọc ẩn danh, trả URL công khai (đặt được CDN phía trước).
     * false = bucket private, mọi URL đều được ký với thời hạn {@link #signedUrlTtlMinutes}.
     */
    private boolean publicRead = true;

    /** Thời hạn signed URL (phút) khi bucket private. */
    private int signedUrlTtlMinutes = 60;

    /** Cạnh dài tối đa của ảnh thu nhỏ (px). 0 = không sinh thumbnail. */
    private int thumbnailMaxEdge = 400;

    /** Ảnh nháp (chưa gắn entity) cũ hơn ngần này giờ sẽ bị job dọn rác xoá. */
    private int orphanRetentionHours = 24;

    /** Bật job dọn ảnh nháp mồ côi. */
    private boolean cleanupJobEnabled = true;

    /** Lịch chạy job dọn rác (cron 6 trường của Spring). */
    private String cleanupJobCron = "0 15 3 * * *";

    public long maxImageSizeBytes() {
        return (long) maxImageSizeMb * 1024 * 1024;
    }
}
