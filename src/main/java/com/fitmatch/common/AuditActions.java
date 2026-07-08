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
    public static final String PT_VERIFY_APPROVE = "PT_VERIFY_APPROVE";
    public static final String PT_VERIFY_REJECT = "PT_VERIFY_REJECT";
    public static final String GYM_VERIFY_APPROVE = "GYM_VERIFY_APPROVE";
    public static final String GYM_VERIFY_REJECT = "GYM_VERIFY_REJECT";
    public static final String GYM_VERIFY_REQUEST_INFO = "GYM_VERIFY_REQUEST_INFO";
    public static final String GYM_SUSPEND = "GYM_SUSPEND";
    public static final String GYM_REACTIVATE = "GYM_REACTIVATE";
    public static final String PT_CREATED_BY_GYM = "PT_CREATED_BY_GYM";
    public static final String PT_STATUS_CHANGE = "PT_STATUS_CHANGE";
    public static final String PT_SUSPEND = "PT_SUSPEND";
    public static final String PT_REACTIVATE = "PT_REACTIVATE";
    public static final String MASTER_DATA_CHANGE = "MASTER_DATA_CHANGE";
    public static final String SYSTEM_CONFIG_CHANGE = "SYSTEM_CONFIG_CHANGE";
    public static final String BOOKING_PAYMENT_HOLD = "BOOKING_PAYMENT_HOLD";
    public static final String BOOKING_ACCEPT = "BOOKING_ACCEPT";
    public static final String BOOKING_REJECT = "BOOKING_REJECT";
    public static final String BOOKING_PT_ASSIGN = "BOOKING_PT_ASSIGN";
}
