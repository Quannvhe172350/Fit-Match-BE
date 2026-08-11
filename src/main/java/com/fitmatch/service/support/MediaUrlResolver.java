package com.fitmatch.service.support;

import com.fitmatch.config.MediaProperties;
import com.fitmatch.entity.MediaAsset;
import com.fitmatch.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Biến {@code storageKey} thành URL mà trình duyệt tải được.
 *
 * <p>Hai chế độ, chọn bằng {@code app.media.public-read}:
 * <ul>
 *   <li><b>public</b> — bucket cho đọc ẩn danh; URL cố định, cache được ở CDN.
 *       URL đã tính sẵn lúc upload nên đường đọc không tốn thêm gọi nào.</li>
 *   <li><b>private</b> — bucket đóng; mỗi response ký lại một URL hết hạn sau
 *       {@code signed-url-ttl-minutes}. Credentials nằm ở backend, FE không bao
 *       giờ nhìn thấy service account.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class MediaUrlResolver {

    private final StorageService storageService;
    private final MediaProperties properties;

    public String urlFor(MediaAsset media) {
        if (media == null) return null;
        if (properties.isPublicRead() && media.getUrl() != null) return media.getUrl();
        return resolveKey(media.getStorageKey());
    }

    public String thumbnailUrlFor(MediaAsset media) {
        if (media == null) return null;
        // Ảnh nhỏ sẵn thì không có bản thu nhỏ riêng — dùng luôn ảnh gốc để FE
        // không phải xử lý trường hợp null.
        return media.getThumbnailKey() == null
                ? urlFor(media)
                : resolveKey(media.getThumbnailKey());
    }

    private String resolveKey(String storageKey) {
        if (storageKey == null) return null;
        if (properties.isPublicRead()) {
            String url = storageService.getUrl(storageKey);
            return url != null ? url : storageService.getSignedUrl(storageKey, ttl());
        }
        return storageService.getSignedUrl(storageKey, ttl());
    }

    private Duration ttl() {
        return Duration.ofMinutes(properties.getSignedUrlTtlMinutes());
    }
}
