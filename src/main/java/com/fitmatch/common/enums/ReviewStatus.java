package com.fitmatch.common.enums;

/** Trạng thái kiểm duyệt review (UC-069/071). */
public enum ReviewStatus {

    /** Hiển thị công khai — tính vào điểm trung bình. */
    VISIBLE,

    /** Bị ẩn tạm bởi moderator (đang xử lý report) — không tính điểm. */
    HIDDEN,

    /** Bị gỡ vĩnh viễn do vi phạm — không tính điểm, không hiển thị. */
    REMOVED
}
