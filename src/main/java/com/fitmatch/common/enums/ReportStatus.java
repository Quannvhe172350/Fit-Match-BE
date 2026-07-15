package com.fitmatch.common.enums;

/** Trạng thái báo cáo review/dịch vụ (UC-070/071). */
public enum ReportStatus {

    /** Chờ moderator xử lý. */
    OPEN,

    /** Đã xử lý (giữ/ẩn/gỡ review kèm ghi chú). */
    RESOLVED,

    /** Bỏ qua — báo cáo không hợp lệ. */
    DISMISSED
}
