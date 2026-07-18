package com.fitmatch.service;

/**
 * Abstraction cho việc gửi email giao dịch (D-02).
 * Hiện dùng impl stub ghi log; sau cắm SMTP/SendGrid thật chỉ cần thay impl,
 * không đụng business logic.
 */
public interface EmailService {

    /** Gửi email chứa link/token xác minh tài khoản (UC-01). */
    void sendVerificationEmail(String to, String token);

    /** Gửi email chứa token đặt lại mật khẩu (UC-04). */
    void sendPasswordResetEmail(String to, String token);

    /**
     * Bug 9 (UC-075): email cho thông báo giao dịch (booking/thanh toán) —
     * gửi kèm bản in-app khi người dùng bật "Email thông báo".
     */
    void sendNotificationEmail(String to, String title, String body, String link);
}
