package com.fitmatch.service.impl;

import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.service.SmsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Gửi SMS qua eSMS.vn (UC-002). Kích hoạt khi SMS_PROVIDER=esms — xem SmsServiceConfig.
 * API: SendMultipleMessage_V4_post_json — CodeResult "100" = thành công.
 * Lỗi gửi ném BusinessException để transaction phát OTP rollback (token không bị
 * lưu mồ côi khi khách không nhận được mã).
 */
@Slf4j
public class EsmsSmsService implements SmsService {

    private static final String ENDPOINT =
            "https://rest.esms.vn/MainService.svc/json/SendMultipleMessage_V4_post_json/";

    private final RestClient restClient;
    private final String apiKey;
    private final String secretKey;
    private final String brandname;
    /** 2 = tin CSKH theo brandname (mặc định); 8 = đầu số cố định. */
    private final String smsType;

    public EsmsSmsService(String apiKey, String secretKey, String brandname, String smsType) {
        this.apiKey = apiKey;
        this.secretKey = secretKey;
        this.brandname = brandname;
        this.smsType = smsType;
        var requestFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public void send(String phoneNumber, String message) {
        Map<String, String> body = new HashMap<>();
        body.put("ApiKey", apiKey);
        body.put("SecretKey", secretKey);
        body.put("Phone", phoneNumber);
        body.put("Content", message);
        body.put("SmsType", smsType);
        if (brandname != null && !brandname.isBlank()) {
            body.put("Brandname", brandname);
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri(ENDPOINT)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            String code = response != null ? String.valueOf(response.get("CodeResult")) : null;
            if (!"100".equals(code)) {
                log.error("eSMS send failed: CodeResult={}, response={}", code, response);
                throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                        "SMS could not be sent - please try again later");
            }
            log.info("eSMS sent to {} (SMSID={})", phoneNumber,
                    response.get("SMSID"));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("eSMS send error to {}: {}", phoneNumber, e.getMessage());
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "SMS could not be sent - please try again later");
        }
    }
}
