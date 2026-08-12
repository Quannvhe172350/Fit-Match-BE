package com.fitmatch.common.enums;

import lombok.Getter;

/**
 * Loại đối tượng mà một {@link com.fitmatch.entity.MediaAsset} gắn vào (V64).
 * Media dùng quan hệ polymorphic (entity_type + entity_id) thay vì mỗi bảng một
 * cột ảnh — thêm chỗ dùng ảnh mới chỉ cần thêm hằng số ở đây, không phải migration.
 *
 * <p>{@code folder} là tiền tố object key trên GCS; đổi giá trị này = mồ côi toàn bộ
 * ảnh cũ, nên chỉ thêm mới chứ không sửa.
 */
@Getter
public enum MediaEntityType {

    USER("users"),
    GYM("gyms"),
    BRANCH("branches"),
    SERVICE("services"),
    PACKAGE("packages"),
    /** Ảnh khách chụp khi check-in buổi tập — entityId là booking id (UC-046). */
    CHECK_IN("check-ins"),
    /** Hồ sơ PT (pt_profiles). */
    TRAINER("trainers"),
    REVIEW("reviews"),
    /** Cơ sở vật chất của gym (gym_facilities, UC-47..49). */
    FACILITY("facilities");

    private final String folder;

    MediaEntityType(String folder) {
        this.folder = folder;
    }
}
