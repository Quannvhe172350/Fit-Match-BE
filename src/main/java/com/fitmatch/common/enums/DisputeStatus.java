package com.fitmatch.common.enums;

/** Vòng đời tranh chấp (UC-063..068). */
public enum DisputeStatus {

    /** Mới mở, chờ moderator (UC-063). */
    OPEN,

    /** Moderator đang xem xét bằng chứng (UC-065). */
    UNDER_REVIEW,

    /** Đã có quyết định + áp dụng tài chính (UC-066/067). */
    RESOLVED,

    /** Đã đóng hoàn toàn (UC-068). */
    CLOSED,

    /** Chuyển cấp cao hơn (UC-068). */
    ESCALATED
}
