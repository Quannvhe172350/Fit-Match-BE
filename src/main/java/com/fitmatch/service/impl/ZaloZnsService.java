package com.fitmatch.service.impl;

import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.service.SmsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gửi OTP qua Zalo ZNS (UC-002) — kích hoạt khi SMS_PROVIDER=zalozns.
 * Phù hợp đồ án: app ở CHẾ ĐỘ DEVELOPMENT gửi ZNS miễn phí tới các số thuộc
 * danh sách admin/tester của app (developers.zalo.me), template OTP mẫu có sẵn.
 * Tin đến trong Zalo của người nhận (OA gửi), không phải SMS inbox.
 *
 * Token OA (OAuth v4): access token TTL ~25h; refresh token DÙNG MỘT LẦN và
 * được cấp bản mới sau mỗi lần refresh (rotate) — bản mới nhất được ghi ra
 * {@code token-file} để sống sót qua restart (refresh token trong yml chỉ dùng
 * cho lần chạy đầu tiên).
 *
 * Interface SmsService nhận nguyên câu tin nhắn — service này trích mã OTP
 * (chuỗi 6 chữ số đầu tiên) đưa vào tham số template ({@code otp-param}).
 */
@Slf4j
public class ZaloZnsService implements SmsService {

    private static final String ZNS_ENDPOINT = "https://business.openapi.zalo.me/message/template";
    private static final String OAUTH_ENDPOINT = "https://oauth.zaloapp.com/v4/oa/access_token";
    private static final Pattern OTP_PATTERN = Pattern.compile("(\\d{6})");

    private final RestClient restClient;
    private final String appId;
    private final String secretKey;
    private final String initialRefreshToken;
    private final String templateId;
    private final String otpParam;
    private final Path tokenFile;

    /** Trạng thái token trong RAM — refresh trước hạn 5 phút. */
    private String accessToken;
    private String currentRefreshToken;
    private Instant accessTokenExpiry = Instant.EPOCH;

    public ZaloZnsService(String appId, String secretKey, String refreshToken,
                          String templateId, String otpParam, String tokenFilePath) {
        this.appId = appId;
        this.secretKey = secretKey;
        this.initialRefreshToken = refreshToken;
        this.templateId = templateId;
        this.otpParam = otpParam == null || otpParam.isBlank() ? "otp" : otpParam;
        this.tokenFile = Path.of(tokenFilePath == null || tokenFilePath.isBlank()
                ? "./zalo-refresh-token.txt" : tokenFilePath);
        var requestFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public synchronized void send(String phoneNumber, String message) {
        Matcher m = OTP_PATTERN.matcher(message);
        if (!m.find()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "SMS could not be sent - please try again later");
        }
        String code = m.group(1);
        try {
            ensureAccessToken();
            Map<String, Object> body = new HashMap<>();
            body.put("phone", toLocal84(phoneNumber));
            body.put("template_id", templateId);
            body.put("template_data", Map.of(otpParam, code));
            body.put("tracking_id", "fitmatch-otp-" + System.currentTimeMillis());

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri(ZNS_ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("access_token", accessToken)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            long error = response != null ? ((Number) response.getOrDefault("error", -1)).longValue() : -1;
            if (error != 0) {
                log.error("Zalo ZNS send failed: {}", response);
                throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                        "SMS could not be sent - please try again later");
            }
            log.info("Zalo ZNS OTP sent to {} (template {})", phoneNumber, templateId);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Zalo ZNS send error to {}: {}", phoneNumber, e.getMessage());
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "SMS could not be sent - please try again later");
        }
    }

    /** Refresh access token khi chưa có/sắp hết hạn. Refresh token rotate được lưu ra file. */
    private void ensureAccessToken() {
        if (accessToken != null && Instant.now().isBefore(accessTokenExpiry.minusSeconds(300))) {
            return;
        }
        String refreshToken = loadRefreshToken();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("refresh_token", refreshToken);
        form.add("app_id", appId);
        form.add("grant_type", "refresh_token");

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient.post()
                .uri(OAUTH_ENDPOINT)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header("secret_key", secretKey)
                .body(form)
                .retrieve()
                .body(Map.class);
        if (response == null || response.get("access_token") == null) {
            log.error("Zalo OAuth refresh failed: {}", response);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "SMS could not be sent - please try again later");
        }
        accessToken = String.valueOf(response.get("access_token"));
        long expiresIn = Long.parseLong(String.valueOf(response.getOrDefault("expires_in", "90000")));
        accessTokenExpiry = Instant.now().plusSeconds(expiresIn);
        String newRefresh = String.valueOf(response.get("refresh_token"));
        if (newRefresh != null && !"null".equals(newRefresh)) {
            currentRefreshToken = newRefresh;
            persistRefreshToken(newRefresh);
        }
        log.info("Zalo OA access token refreshed (expires in {}s)", expiresIn);
    }

    /** Ưu tiên token rotate mới nhất: RAM -> file -> yml. */
    private String loadRefreshToken() {
        if (currentRefreshToken != null) return currentRefreshToken;
        try {
            if (Files.exists(tokenFile)) {
                String fromFile = Files.readString(tokenFile).trim();
                if (!fromFile.isEmpty()) return fromFile;
            }
        } catch (IOException e) {
            log.warn("Cannot read Zalo token file {}: {}", tokenFile, e.getMessage());
        }
        return initialRefreshToken;
    }

    private void persistRefreshToken(String token) {
        try {
            Files.writeString(tokenFile, token);
        } catch (IOException e) {
            // Không chặn gửi tin — nhưng sau restart sẽ phải lấy refresh token mới thủ công
            log.warn("Cannot persist Zalo refresh token to {}: {}", tokenFile, e.getMessage());
        }
    }

    /** 0xxxxxxxxx -> 84xxxxxxxxx (ZNS yêu cầu định dạng 84, không dấu +). */
    static String toLocal84(String phone) {
        String p = phone.replaceAll("[^0-9]", "");
        if (p.startsWith("84")) return p;
        if (p.startsWith("0")) return "84" + p.substring(1);
        return p;
    }
}
