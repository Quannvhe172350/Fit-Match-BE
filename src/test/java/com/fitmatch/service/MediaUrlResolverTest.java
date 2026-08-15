package com.fitmatch.service;

import com.fitmatch.config.MediaProperties;
import com.fitmatch.entity.MediaAsset;
import com.fitmatch.service.support.MediaUrlResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * URL ảnh phải dựng lại từ storageKey theo storage ĐANG chạy. Cột
 * {@code media_assets.url} là ảnh chụp lúc upload: bản ghi tạo khi còn chạy
 * storage local mang localhost vĩnh viễn, và sau khi bật GCS thì mọi ảnh cũ vỡ.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MediaUrlResolverTest {

    private static final String KEY = "users/9/avatar/abc.jpg";
    private static final String GCS_URL = "https://storage.googleapis.com/fit-match/" + KEY;
    private static final String STALE_LOCAL_URL = "http://localhost:8080/api/media/raw/" + KEY;

    @Mock private StorageService storageService;
    @Mock private MediaProperties properties;
    @InjectMocks private MediaUrlResolver resolver;

    private MediaAsset media(String storageKey, String storedUrl) {
        return MediaAsset.builder().id(1L).storageKey(storageKey).url(storedUrl).build();
    }

    @Test
    void publicBucket_rebuildsUrlAndIgnoresStaleSnapshot() {
        when(properties.isPublicRead()).thenReturn(true);
        when(storageService.getUrl(KEY)).thenReturn(GCS_URL);

        assertThat(resolver.urlFor(media(KEY, STALE_LOCAL_URL))).isEqualTo(GCS_URL);
    }

    /** Bản ghi cũ thiếu storageKey thì URL đã lưu là thứ duy nhất còn lại. */
    @Test
    void withoutStorageKey_fallsBackToStoredUrl() {
        when(properties.isPublicRead()).thenReturn(true);

        assertThat(resolver.urlFor(media(null, STALE_LOCAL_URL))).isEqualTo(STALE_LOCAL_URL);
    }

    /** Storage không cho đọc ẩn danh -> ký URL, vẫn không đụng tới snapshot. */
    @Test
    void publicBucketWithoutAnonymousRead_signsInstead() {
        when(properties.isPublicRead()).thenReturn(true);
        when(properties.getSignedUrlTtlMinutes()).thenReturn(15);
        when(storageService.getUrl(KEY)).thenReturn(null);
        when(storageService.getSignedUrl(eq(KEY), any(Duration.class))).thenReturn("https://signed/x");

        assertThat(resolver.urlFor(media(KEY, STALE_LOCAL_URL))).isEqualTo("https://signed/x");
    }

    @Test
    void privateBucket_alwaysSigns() {
        when(properties.isPublicRead()).thenReturn(false);
        when(properties.getSignedUrlTtlMinutes()).thenReturn(15);
        when(storageService.getSignedUrl(eq(KEY), any(Duration.class))).thenReturn("https://signed/x");

        assertThat(resolver.urlFor(media(KEY, STALE_LOCAL_URL))).isEqualTo("https://signed/x");
        assertThat(resolver.isStableUrl()).isFalse();
    }

    /** Chỉ URL của bucket public mới đem đi lưu vào cột cache được. */
    @Test
    void publicBucket_urlIsStableEnoughToCache() {
        when(properties.isPublicRead()).thenReturn(true);

        assertThat(resolver.isStableUrl()).isTrue();
    }

    @Test
    void thumbnail_rebuiltFromItsOwnKey() {
        when(properties.isPublicRead()).thenReturn(true);
        MediaAsset asset = media(KEY, STALE_LOCAL_URL);
        asset.setThumbnailKey("users/9/avatar/thumb/abc.jpg");
        when(storageService.getUrl("users/9/avatar/thumb/abc.jpg"))
                .thenReturn("https://storage.googleapis.com/fit-match/users/9/avatar/thumb/abc.jpg");

        assertThat(resolver.thumbnailUrlFor(asset))
                .isEqualTo("https://storage.googleapis.com/fit-match/users/9/avatar/thumb/abc.jpg");
    }
}
