package com.fitmatch.common.enums;

/**
 * Chiều của một giao dịch trên sao kê ngân hàng do Casso đồng bộ (V61).
 * Trước đây bảng {@code payment_transactions} chỉ ghi tiền vào; nay ghi cả tiền
 * ra để đối soát lệnh chi trả cho gym/PT/khách hàng.
 */
public enum PaymentTxnDirection {

    /** Tiền vào tài khoản nền tảng — khách thanh toán booking, khớp payment_orders. */
    IN,

    /** Tiền ra khỏi tài khoản nền tảng — chi trả lệnh rút, khớp withdrawal_requests. */
    OUT
}
