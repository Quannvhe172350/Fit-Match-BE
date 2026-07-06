package com.fitmatch.common.enums;

/**
 * Vòng đời hiển thị của dịch vụ/gói tập trên marketplace (UC-027).
 */
public enum CatalogStatus {

    /** Đang bán — hiển thị trên marketplace và đặt được. */
    PUBLISHED,

    /** Ẩn khỏi marketplace (nháp/tạm ẩn) — có thể publish lại. */
    HIDDEN,

    /** Tạm ngưng nhận booking nhưng vẫn có thể hiển thị thông tin — có thể publish lại. */
    PAUSED,

    /** Ngưng vĩnh viễn — không thể chuyển sang trạng thái khác. */
    ARCHIVED
}
