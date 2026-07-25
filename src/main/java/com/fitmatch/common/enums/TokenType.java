package com.fitmatch.common.enums;

/**
 * Loại token một lần dùng cho luồng xác thực qua email.
 */
public enum TokenType {

    /** Xác minh địa chỉ email sau khi đăng ký (UC-01). */
    EMAIL_VERIFICATION,

    /** Đặt lại mật khẩu khi quên (UC-04). */
    PASSWORD_RESET,

    /** Xác minh số điện thoại bằng OTP 6 chữ số (UC-002). */
    PHONE_VERIFICATION
}
