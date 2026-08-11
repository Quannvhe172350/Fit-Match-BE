package com.fitmatch.dto.media;

import com.fitmatch.common.enums.MediaEntityType;
import com.fitmatch.common.enums.MediaImageType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Ảnh trả về cho client. Không lộ {@code storageKey}/bucket — FE chỉ cần URL;
 * để lộ key nội bộ chẳng thêm giá trị gì mà lại tiết lộ cấu trúc bucket.
 */
@Getter
@Builder
public class MediaResponse {

    private Long id;
    private MediaEntityType entityType;
    private Long entityId;
    private MediaImageType imageType;
    private String url;
    private String thumbnailUrl;
    private String originalName;
    private String mimeType;
    private long fileSize;
    private Integer width;
    private Integer height;
    private String caption;
    private int sortOrder;
    private boolean primary;
    private LocalDateTime createdAt;
}
