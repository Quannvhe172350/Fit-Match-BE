package com.fitmatch.service.impl;

import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.service.SmsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gửi SMS qua SpeedSMS.vn (UC-002) — kích hoạt khi SMS_PROVIDER=speedsms.
 * Dịch vụ VN: đăng ký tặng credit test, không chặn số Việt Nam như Twilio trial.
 * API: POST /index.php/sms/send, Basic Auth (username = access token).
 * sms_type: 2 = CSKH brandname đã đăng ký; 3 = brandname "SpeedSMS" (dùng để test
 * với credit tặng); 4 = đầu số cố định.
 * Lỗi gửi ném BusinessException để transaction phát OTP rollback.
 */
@Slf4j
public class SpeedSmsService implements SmsService {

    private static final String ENDPOINT = "https://api.speedsms.vn/index.php/sms/send";

    private final RestClient restClient;
    private final int smsType;
    private final String sender;

    public SpeedSmsService(String accessToken, int smsType, String sender) {
        this.smsType = smsType;
        this.sender = sender;
        var requestFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeaders(h -> h.setBasicAuth(accessToken, "x"))
                .build();
    }

    @Override
    public void send(String phoneNumber, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("to", List.of(toLocal84(phoneNumber)));
        body.put("content", message);
        body.put("sms_type", smsType);
        if (sender != null && !sender.isBlank()) {
            body.put("sender", sender);
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri(ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            String status = response != null ? String.valueOf(response.get("status")) : null;
            if (!"success".equalsIgnoreCase(status)) {
                log.error("SpeedSMS send failed: response={}", response);
                throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                        "SMS could not be sent - please try again later");
            }
            log.info("SpeedSMS sent to {} (type={})", phoneNumber, smsType);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("SpeedSMS send error to {}: {}", phoneNumber, e.getMessage());
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "SMS could not be sent - please try again later");
        }
    }

    /** 0xxxxxxxxx -> 84xxxxxxxxx (SpeedSMS nhận dạng không dấu +). */
    static String toLocal84(String phone) {
        String p = phone.replaceAll("[^0-9]", "");
        if (p.startsWith("84")) return p;
        if (p.startsWith("0")) return "84" + p.substring(1);
        return p;
    }
}
