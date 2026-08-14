package com.fitmatch.service;

import com.fitmatch.common.enums.MediaEntityType;
import com.fitmatch.common.enums.MediaImageType;
import com.fitmatch.dto.media.MediaResponse;
import com.fitmatch.service.support.PtAvatarResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Ảnh đại diện hồ sơ PT đọc từ media TRAINER/AVATAR — cùng loại ảnh mà
 * MediaAccessGuard cho phép PT tự sửa và gym chủ quản sửa hộ.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PtAvatarResolverTest {

    @Mock private MediaService mediaService;
    @InjectMocks private PtAvatarResolver resolver;

    private MediaResponse media(String url) {
        return MediaResponse.builder().url(url).build();
    }

    @Test
    void urlOf_readsTrainerAvatar() {
        when(mediaService.primaryFor(MediaEntityType.TRAINER, 7L, MediaImageType.AVATAR))
                .thenReturn(media("https://cdn/trainers/7/avatar.jpg"));

        assertThat(resolver.urlOf(7L)).isEqualTo("https://cdn/trainers/7/avatar.jpg");
    }

    @Test
    void urlOf_withoutMedia_isNull() {
        when(mediaService.primaryFor(any(), any(), any())).thenReturn(null);

        assertThat(resolver.urlOf(7L)).isNull();
    }

    /** Danh sách phải đi bằng MỘT truy vấn gộp, không lặp primaryFor từng PT. */
    @Test
    void urlsOf_batchesAndKeepsFirstImagePerPt() {
        when(mediaService.listForEntities(MediaEntityType.TRAINER, List.of(1L, 2L, 3L),
                MediaImageType.AVATAR))
                .thenReturn(Map.of(
                        1L, List.of(media("a.jpg"), media("b.jpg")),
                        2L, List.of(),
                        3L, List.of(media("c.jpg"))));

        Map<Long, String> urls = resolver.urlsOf(List.of(1L, 2L, 3L));

        assertThat(urls).containsOnlyKeys(1L, 3L)
                .containsEntry(1L, "a.jpg")
                .containsEntry(3L, "c.jpg");
        verify(mediaService, never()).primaryFor(any(), any(), any());
    }
}
