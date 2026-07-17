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
    public static final String BOOKING_CANCEL_BY_GYM = "BOOKING_CANCEL_BY_GYM";
    public static final String BOOKING_NO_SHOW = "BOOKING_NO_SHOW";
    public static final String COMMISSION_CONFIG_CHANGE = "COMMISSION_CONFIG_CHANGE";
    public static final String BOOKING_COMPLETE = "BOOKING_COMPLETE";
    public static final String SETTLEMENT_PENDING = "SETTLEMENT_PENDING";
    public static final String SETTLEMENT_RELEASE = "SETTLEMENT_RELEASE";
    public static final String REFUND_REQUEST_CREATE = "REFUND_REQUEST_CREATE";
    public static final String REFUND_EXECUTE = "REFUND_EXECUTE";
    public static final String REFUND_REJECT = "REFUND_REJECT";
    public static final String WITHDRAWAL_REQUEST = "WITHDRAWAL_REQUEST";
    public static final String WITHDRAWAL_APPROVE = "WITHDRAWAL_APPROVE";
    public static final String WITHDRAWAL_REJECT = "WITHDRAWAL_REJECT";
    public static final String WITHDRAWAL_PAID = "WITHDRAWAL_PAID";
    public static final String PAYMENT_ORDER_EXPIRE = "PAYMENT_ORDER_EXPIRE";
    public static final String BOOKING_CHECK_IN = "BOOKING_CHECK_IN";
    public static final String ATTENDANCE_CORRECT = "ATTENDANCE_CORRECT";
    public static final String REVIEW_MODERATE = "REVIEW_MODERATE";
    public static final String REVIEW_REPORT_RESOLVE = "REVIEW_REPORT_RESOLVE";
    public static final String WALLET_FREEZE = "WALLET_FREEZE";
    public static final String WALLET_UNFREEZE = "WALLET_UNFREEZE";
    public static final String DISPUTE_OPEN = "DISPUTE_OPEN";
    public static final String DISPUTE_REVIEW = "DISPUTE_REVIEW";
    public static final String DISPUTE_RESOLVE = "DISPUTE_RESOLVE";
    public static final String DISPUTE_CLOSE = "DISPUTE_CLOSE";
    public static final String DISPUTE_ESCALATE = "DISPUTE_ESCALATE";
}
