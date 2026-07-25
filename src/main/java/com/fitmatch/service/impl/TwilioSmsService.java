package com.fitmatch.service.impl;

import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.service.SmsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

/**
 * Gửi SMS qua Twilio (UC-002) — kích hoạt khi SMS_PROVIDER=twilio.
 * Phù hợp đồ án/demo: tài khoản TRIAL miễn phí (~$15 credit, không cần thẻ),
 * nhưng chỉ gửi được tới số đã verify trong console Twilio và tin có prefix
 * "Sent from your Twilio trial account".
 * SĐT Việt Nam dạng 0xxxxxxxxx được tự chuyển sang E.164 (+84xxxxxxxxx).
 */
@Slf4j
public class TwilioSmsService implements SmsService {

    private final RestClient restClient;
    private final String accountSid;
    private final String fromNumber;

    public TwilioSmsService(String accountSid, String authToken, String fromNumber) {
        this.accountSid = accountSid;
        this.fromNumber = fromNumber;
        var requestFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeaders(h -> h.setBasicAuth(accountSid, authToken))
                .build();
    }

    @Override
    public void send(String phoneNumber, String message) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("To", toE164(phoneNumber));
        form.add("From", fromNumber);
        form.add("Body", message);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri("https://api.twilio.com/2010-04-01/Accounts/" + accountSid + "/Messages.json")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
            log.info("Twilio SMS queued to {} (sid={})", phoneNumber,
                    response != null ? response.get("sid") : null);
        } catch (Exception e) {
            // Twilio trả 4xx kèm body mô tả (vd: số chưa verify với tài khoản trial)
            log.error("Twilio send error to {}: {}", phoneNumber, e.getMessage());
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "SMS could not be sent - please try again later");
        }
    }

    /** 0xxxxxxxxx (VN) -> +84xxxxxxxxx; số đã có +/mã quốc gia giữ nguyên. */
    static String toE164(String phone) {
        String p = phone.replaceAll("[^0-9+]", "");
        if (p.startsWith("+")) return p;
        if (p.startsWith("84")) return "+" + p;
        if (p.startsWith("0")) return "+84" + p.substring(1);
        return "+" + p;
    }
}
