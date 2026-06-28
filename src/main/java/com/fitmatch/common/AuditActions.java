package com.fitmatch.common;

/**
 * Tên hành động dùng cho {@link com.fitmatch.service.AuditService}. Tập trung để tránh chuỗi rải rác.
 */
public final class AuditActions {

    private AuditActions() {
    }

    public static final String USER_LOCK = "USER_LOCK";
    public static final String USER_UNLOCK = "USER_UNLOCK";
    public static final String USER_STATUS_CHANGE = "USER_STATUS_CHANGE";
    public static final String USER_ROLE_ASSIGN = "USER_ROLE_ASSIGN";
}
