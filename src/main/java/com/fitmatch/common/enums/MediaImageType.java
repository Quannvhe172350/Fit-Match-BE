package com.fitmatch.common.enums;

import lombok.Getter;

/**
 * Vai trò của ảnh trong một entity (V64). {@code singleton = true} nghĩa là entity
 * chỉ giữ đúng một ảnh loại đó — upload ảnh mới sẽ thay thế (và xoá) ảnh cũ.
 */
@Getter
public enum MediaImageType {

    /** Ảnh đại diện — 1 ảnh/entity. */
    AVATAR("avatar", true),
    /** Ảnh bìa — 1 ảnh/entity. */
    COVER("cover", true),
    /** Thư viện ảnh — nhiều ảnh, sắp xếp theo sortOrder. */
    GALLERY("gallery", false),
    /** Ảnh thu nhỏ do hệ thống sinh (không upload trực tiếp). */
    THUMBNAIL("thumb", true),
    /** Ảnh đính kèm đánh giá (UC-069). */
    REVIEW_IMAGE("images", false),
    /** Ảnh đính kèm check-in buổi tập (UC-046). */
    CHECKIN_IMAGE("images", false);

    private final String folder;
    private final boolean singleton;

    MediaImageType(String folder, boolean singleton) {
        this.folder = folder;
        this.singleton = singleton;
    }
}
