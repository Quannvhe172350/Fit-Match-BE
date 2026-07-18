package com.fitmatch.service.impl;

import com.fitmatch.service.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

@Slf4j
public class SmtpEmailService implements EmailService {

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String frontendUrl;

    public SmtpEmailService(JavaMailSender mailSender, String fromAddress, String frontendUrl) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.frontendUrl = frontendUrl;
    }

    // Phase 5: gửi email ngoài luồng request (executor "email-*") — request
    // register/forgot-password không còn chờ SMTP; lỗi gửi được log, không nổ 500.
    @Override
    @org.springframework.scheduling.annotation.Async("emailExecutor")
    public void sendVerificationEmail(String to, String token) {
        String link = frontendUrl + "/verify-email?token=" + token;
        send(to, "FitMatch — Xác thực tài khoản của bạn", buildVerificationHtml(link));
        log.info("Verification email sent to {}", to);
    }

    @Override
    @org.springframework.scheduling.annotation.Async("emailExecutor")
    public void sendPasswordResetEmail(String to, String token) {
        String link = frontendUrl + "/reset-password?token=" + token;
        send(to, "FitMatch — Đặt lại mật khẩu", buildPasswordResetHtml(link));
        log.info("Password reset email sent to {}", to);
    }

    // Bug 9 (UC-075): email thông báo giao dịch (booking/thanh toán).
    @Override
    @org.springframework.scheduling.annotation.Async("emailExecutor")
    public void sendNotificationEmail(String to, String title, String body, String link) {
        String cta = link != null && !link.isBlank() ? frontendUrl + link : frontendUrl;
        send(to, "FitMatch — " + title, buildNotificationHtml(title, body, cta));
        log.info("Notification email sent to {} ({})", to, title);
    }

    private void send(String to, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
        } catch (MessagingException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
            throw new RuntimeException("Failed to send email", e);
        }
    }

    private String buildVerificationHtml(String link) {
        return """
                <div style="font-family:sans-serif;max-width:560px;margin:0 auto;padding:32px 24px">
                  <h2 style="color:#1B2A3E;margin-bottom:8px">Xác thực tài khoản FitMatch</h2>
                  <p style="color:#64748B;line-height:1.6">
                    Cảm ơn bạn đã đăng ký! Nhấn nút bên dưới để xác thực địa chỉ email của bạn.
                    Link có hiệu lực trong <strong>24 giờ</strong>.
                  </p>
                  <a href="%s"
                     style="display:inline-block;margin-top:24px;padding:12px 28px;background:#2563EB;color:#fff;
                            border-radius:6px;text-decoration:none;font-weight:600">
                    Xác thực Email
                  </a>
                  <p style="margin-top:24px;font-size:12px;color:#94A3B8">
                    Nếu bạn không tạo tài khoản này, hãy bỏ qua email này.
                  </p>
                </div>
                """.formatted(link);
    }

    private String buildNotificationHtml(String title, String body, String ctaLink) {
        return """
                <div style="font-family:sans-serif;max-width:560px;margin:0 auto;padding:32px 24px">
                  <h2 style="color:#1B2A3E;margin-bottom:8px">%s</h2>
                  <p style="color:#64748B;line-height:1.6">%s</p>
                  <a href="%s"
                     style="display:inline-block;margin-top:24px;padding:12px 28px;background:#2563EB;color:#fff;
                            border-radius:6px;text-decoration:none;font-weight:600">
                    Xem chi tiết
                  </a>
                  <p style="margin-top:24px;font-size:12px;color:#94A3B8">
                    Bạn nhận được email này vì đã bật "Email thông báo" trong cài đặt FitMatch.
                  </p>
                </div>
                """.formatted(title, body, ctaLink);
    }

    private String buildPasswordResetHtml(String link) {
        return """
                <div style="font-family:sans-serif;max-width:560px;margin:0 auto;padding:32px 24px">
                  <h2 style="color:#1B2A3E;margin-bottom:8px">Đặt lại mật khẩu FitMatch</h2>
                  <p style="color:#64748B;line-height:1.6">
                    Nhấn nút bên dưới để đặt lại mật khẩu của bạn.
                    Link có hiệu lực trong <strong>30 phút</strong>.
                  </p>
                  <a href="%s"
                     style="display:inline-block;margin-top:24px;padding:12px 28px;background:#2563EB;color:#fff;
                            border-radius:6px;text-decoration:none;font-weight:600">
                    Đặt lại mật khẩu
                  </a>
                  <p style="margin-top:24px;font-size:12px;color:#94A3B8">
                    Nếu bạn không yêu cầu reset mật khẩu, hãy bỏ qua email này.
                  </p>
                </div>
                """.formatted(link);
    }
}
