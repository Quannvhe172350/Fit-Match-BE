package com.fitmatch.dto.gym;

import com.fitmatch.common.enums.MediaEntityType;
import com.fitmatch.common.enums.MediaImageType;
import com.fitmatch.dto.media.MediaResponse;
import com.fitmatch.entity.GymMedia;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ảnh của Gym/chi nhánh cho FE (UC-016). Giữ nguyên hình dạng JSON cũ
 * (id/url/caption/branchId) để client hiện tại không phải sửa, đồng thời bổ sung
 * thumbnail + cờ ảnh chính từ Media system mới (V64).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GymMediaResponse {

    private Long id;
    private String url;
    private String thumbnailUrl;
    private String caption;
    private Long branchId;
    private MediaImageType imageType;
    private int sortOrder;
    private boolean primary;

    /** @deprecated bảng {@code gym_media} đã được thay bằng {@code media_assets} (V64). */
    @Deprecated(forRemoval = true)
    public static GymMediaResponse of(GymMedia m) {
        return GymMediaResponse.builder()
                .id(m.getId())
                .url(m.getUrl())
                .thumbnailUrl(m.getUrl())
                .caption(m.getCaption())
                .branchId(m.getGymBranch() != null ? m.getGymBranch().getId() : null)
                .imageType(MediaImageType.GALLERY)
                .build();
    }

    public static GymMediaResponse of(MediaResponse m) {
        return GymMediaResponse.builder()
                .id(m.getId())
                .url(m.getUrl())
                .thumbnailUrl(m.getThumbnailUrl())
                .caption(m.getCaption())
                .branchId(m.getEntityType() == MediaEntityType.BRANCH ? m.getEntityId() : null)
                .imageType(m.getImageType())
                .sortOrder(m.getSortOrder())
                .primary(m.isPrimary())
                .build();
    }
}
