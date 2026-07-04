package com.fitmatch.common.enums;

/**
 * Trạng thái hoạt động của PT dưới quyền quản lý của Gym (UC-019, UC-021).
 * Thay thế cơ chế PT tự verify với platform (đã bỏ theo Use Case mới).
 */
public enum PtStatus {

    /** Đang hoạt động — hiển thị trên marketplace (khi Gym cũng đang hiển thị) và nhận booking. */
    ACTIVE,

    /** Gym tạm tắt — không hiển thị, không nhận booking; Gym có thể bật lại. */
    INACTIVE,

    /** Bị đình chỉ bởi Admin (sự cố chất lượng/an toàn, UC-021) — chỉ Admin gỡ được. */
    SUSPENDED
}
