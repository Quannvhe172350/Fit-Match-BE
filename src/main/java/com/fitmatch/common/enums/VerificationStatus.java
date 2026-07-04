package com.fitmatch.common.enums;

/**
 * Trạng thái xác minh hồ sơ PT (UC-23..30) và Gym (UC-41..46).
 */
public enum VerificationStatus {

    /** Chưa nộp hồ sơ. */
    NOT_SUBMITTED,

    /** Đã nộp, chờ Admin duyệt. */
    PENDING,

    /** Đã được duyệt — hiển thị trên marketplace. */
    APPROVED,

    /** Bị từ chối — có thể nộp lại (resubmit). */
    REJECTED,

    /** Admin yêu cầu bổ sung hồ sơ (UC-013) — chủ hồ sơ bổ sung rồi nộp lại. */
    REQUIRES_INFO,

    /** Bị đình chỉ do vi phạm chính sách (UC-014) — Admin có thể kích hoạt lại. */
    SUSPENDED
}
