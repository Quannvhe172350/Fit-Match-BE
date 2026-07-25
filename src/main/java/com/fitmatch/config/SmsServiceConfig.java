package com.fitmatch.config;

import com.fitmatch.service.SmsService;
import com.fitmatch.service.impl.EsmsSmsService;
import com.fitmatch.service.impl.LoggingSmsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * UC-002: chọn kênh gửi SMS theo config (cùng pattern EmailServiceConfig).
 * - SMS_PROVIDER=esms (+ ESMS_API_KEY/ESMS_SECRET_KEY) -> gửi thật qua eSMS.vn.
 * - Không cấu hình -> LoggingSmsService (log mã ra console, dev đọc từ log).
 * Thêm nhà cung cấp khác: viết impl SmsService + thêm @Bean @ConditionalOnProperty
 * havingValue tương ứng ở đây.
 */
@Configuration
public class SmsServiceConfig {

    @Bean
    @ConditionalOnProperty(name = "app.sms.provider", havingValue = "esms")
    public SmsService esmsSmsService(
            @Value("${app.sms.esms.api-key}") String apiKey,
            @Value("${app.sms.esms.secret-key}") String secretKey,
            @Value("${app.sms.esms.brandname:}") String brandname,
            @Value("${app.sms.esms.sms-type:2}") String smsType) {
        return new EsmsSmsService(apiKey, secretKey, brandname, smsType);
    }

    /**
     * Zalo ZNS — OTP vào Zalo người nhận qua OA. Chế độ development của app
     * (developers.zalo.me) gửi MIỄN PHÍ tới các số admin/tester — hợp đồ án/demo.
     */
    @Bean
    @ConditionalOnProperty(name = "app.sms.provider", havingValue = "zalozns")
    public SmsService zaloZnsService(
            @Value("${app.sms.zalozns.app-id}") String appId,
            @Value("${app.sms.zalozns.secret-key}") String secretKey,
            @Value("${app.sms.zalozns.refresh-token}") String refreshToken,
            @Value("${app.sms.zalozns.template-id}") String templateId,
            @Value("${app.sms.zalozns.otp-param:otp}") String otpParam,
            @Value("${app.sms.zalozns.token-file:./zalo-refresh-token.txt}") String tokenFile) {
        return new com.fitmatch.service.impl.ZaloZnsService(
                appId, secretKey, refreshToken, templateId, otpParam, tokenFile);
    }

    /** SpeedSMS.vn — dịch vụ VN, tặng credit test, không chặn số VN như Twilio trial. */
    @Bean
    @ConditionalOnProperty(name = "app.sms.provider", havingValue = "speedsms")
    public SmsService speedSmsService(
            @Value("${app.sms.speedsms.access-token}") String accessToken,
            @Value("${app.sms.speedsms.sms-type:2}") int smsType,
            @Value("${app.sms.speedsms.sender:}") String sender) {
        return new com.fitmatch.service.impl.SpeedSmsService(accessToken, smsType, sender);
    }

    /** Twilio trial miễn phí — phù hợp đồ án/demo (chỉ gửi được tới số đã verify). */
    @Bean
    @ConditionalOnProperty(name = "app.sms.provider", havingValue = "twilio")
    public SmsService twilioSmsService(
            @Value("${app.sms.twilio.account-sid}") String accountSid,
            @Value("${app.sms.twilio.auth-token}") String authToken,
            @Value("${app.sms.twilio.from-number}") String fromNumber) {
        return new com.fitmatch.service.impl.TwilioSmsService(accountSid, authToken, fromNumber);
    }

    @Bean
    @ConditionalOnMissingBean(SmsService.class)
    public SmsService loggingSmsService() {
        return new LoggingSmsService();
    }
}
