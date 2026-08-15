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
 *       Dựng URL chỉ là ghép chuỗi {@code https://storage.googleapis.com/bucket/key}
 *       nên không tốn lượt gọi nào.</li>
 *   <li><b>private</b> — bucket đóng; mỗi response ký lại một URL hết hạn sau
 *       {@code signed-url-ttl-minutes}. Credentials nằm ở backend, FE không bao
 *       giờ nhìn thấy service account.</li>
 * </ul>
 *
 * <p><b>Luôn dựng lại từ {@code storageKey}</b>, cột {@code media_assets.url} chỉ
 * là phương án cuối. Cột đó là ảnh chụp tại thời điểm upload: bản ghi tạo lúc
 * ứng dụng chạy storage local mang sẵn {@code http://localhost:8080/api/media/raw/...}
 * và giữ nguyên vĩnh viễn, nên sau khi bật GCS mọi ảnh cũ vẫn trỏ về máy dev và
 * không hiện được. Khoá + bucket hiện hành mới là nguồn sự thật.
 */
@Component
@RequiredArgsConstructor
public class MediaUrlResolver {

    private final StorageService storageService;
    private final MediaProperties properties;

    public String urlFor(MediaAsset media) {
        if (media == null) return null;
        String resolved = resolveKey(media.getStorageKey());
        // Chỉ rơi về URL đã lưu khi không dựng lại được (bản ghi cũ thiếu
        // storageKey) — chứ không ưu tiên nó, xem javadoc lớp.
        return resolved != null ? resolved : media.getUrl();
    }

    public String thumbnailUrlFor(MediaAsset media) {
        if (media == null) return null;
        // Ảnh nhỏ sẵn thì không có bản thu nhỏ riêng — dùng luôn ảnh gốc để FE
        // không phải xử lý trường hợp null.
        return media.getThumbnailKey() == null
                ? urlFor(media)
                : resolveKey(media.getThumbnailKey());
    }

    /**
     * URL sinh ra có ổn định giữa các lần gọi không. Bucket public cho URL cố
     * định (lưu vào cột cache được); bucket private ký lại mỗi lần và URL hết
     * hạn, nên KHÔNG được đem đi lưu ở đâu cả.
     */
    public boolean isStableUrl() {
        return properties.isPublicRead();
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
