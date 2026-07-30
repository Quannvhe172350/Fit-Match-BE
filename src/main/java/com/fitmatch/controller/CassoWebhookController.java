package com.fitmatch.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.config.PaymentProperties;
import com.fitmatch.dto.payment.CassoWebhookRequest;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.service.PaymentWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/webhooks/casso")
@RequiredArgsConstructor
@Tag(name = "E. Payment Webhook", description = "Webhook đối soát Casso (UC-053). Xác thực bằng header X-Casso-Signature (HMAC-SHA512).")
@SecurityRequirements
public class CassoWebhookController {

    private final PaymentWebhookService paymentWebhookService;
    private final PaymentProperties paymentProperties;
    private final ObjectMapper objectMapper;

    @Operation(
            summary = "UC-053 — Nhận webhook đối soát Casso",
            description = """
                    Actor: **Casso (System)**. Xác thực bằng header `X-Casso-Signature`
                    (định dạng `t=<unix_millis>,v1=<hex_hmac>`) — HMAC-SHA512
                    của `timestamp + "." + sortObjDataByKey(body)` với key `app.casso.webhook-secret`.
                    Casso sắp xếp key JSON đệ quy A→Z trước khi ký; ta phải tái tạo
                    đúng chuỗi đã ký bằng cách sort giống hệt.
                    Idempotent theo id giao dịch Casso.""")
    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> receive(HttpServletRequest httpRequest) {
        String secret = paymentProperties.getCasso().getWebhookSecret();
        String signature = httpRequest.getHeader("X-Casso-Signature");

        log.info("Casso webhook received: X-Casso-Signature={}, secretConfigured={}",
                signature != null ? signature.substring(0, Math.min(30, signature.length())) + "..." : "MISSING",
                secret != null && !secret.isBlank());

        if (secret == null || secret.isBlank()) {
            log.warn("Casso webhook rejected: CASSO_WEBHOOK_SECRET is not configured");
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Webhook secret not configured");
        }
        if (signature == null || signature.isBlank()) {
            log.warn("Casso webhook rejected: X-Casso-Signature header missing");
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Missing X-Casso-Signature header");
        }

        // Đọc raw body một lần — dùng cho verify signature và deserialize
        String rawBody;
        try {
            rawBody = httpRequest.getReader().lines().collect(Collectors.joining());
        } catch (Exception e) {
            log.error("Failed to read webhook request body", e);
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Cannot read request body");
        }

        // Verify HMAC-SHA512 với JSON đã sort key (giống Casso ký)
        if (!verifySignature(signature, secret, rawBody)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid webhook signature");
        }

        // Parse JSON
        CassoWebhookRequest request;
        try {
            request = objectMapper.readValue(rawBody, CassoWebhookRequest.class);
        } catch (Exception e) {
            log.error("Failed to parse Casso webhook body", e);
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Malformed webhook body");
        }

        int matched = paymentWebhookService.processCasso(request);
        return ResponseEntity.ok(ApiResponse.success("Webhook processed",
                Map.of("matched", matched)));
    }

    /**
     * Sort key JSON đệ quy A→Z — GIỐNG HỆT hàm Casso dùng để tạo chữ ký.
     * Tham khảo: github.com/CassoHQ/casso-webhook-v2-verify-signature
     */
    @SuppressWarnings("unchecked")
    static Object sortJsonKeys(Object input) {
        if (input instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) input;
            TreeMap<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                sorted.put(entry.getKey(), sortJsonKeys(entry.getValue()));
            }
            return sorted;
        }
        if (input instanceof List) {
            List<Object> list = (List<Object>) input;
            return list.stream()
                    .map(CassoWebhookController::sortJsonKeys)
                    .toList();
        }
        return input;
    }

    private boolean verifySignature(String signatureHeader, String secret, String rawBody) {
        try {
            String[] parts = signatureHeader.split(",");
            if (parts.length < 2) return false;

            String tPart = parts[0].trim();
            String v1Part = parts[1].trim();
            if (!tPart.startsWith("t=") || !v1Part.startsWith("v1=")) return false;

            String timestamp = tPart.substring(2);
            String expectedHmac = v1Part.substring(3);

            // Parse body → sort keys đệ quy → serialize lại (compact, không space).
            // Casso ký trên JSON đã sort key; nếu dùng raw body trực tiếp key order
            // có thể khác → HMAC không khớp.
            Object bodyJson = objectMapper.readValue(rawBody, Object.class);
            Object sorted = sortJsonKeys(bodyJson);
            String sortedBody = objectMapper.writeValueAsString(sorted);

            String data = timestamp + "." + sortedBody;
            Mac mac = Mac.getInstance("HmacSHA512");
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            mac.init(keySpec);
            byte[] computed = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            String computedHex = bytesToHex(computed);

            boolean valid = java.security.MessageDigest.isEqual(
                    expectedHmac.getBytes(StandardCharsets.UTF_8),
                    computedHex.getBytes(StandardCharsets.UTF_8));
            if (!valid) {
                log.warn("Casso signature mismatch: timestamp={}, expectedHmac={}, computedHmac={}",
                        timestamp,
                        expectedHmac.substring(0, Math.min(20, expectedHmac.length())) + "...",
                        computedHex.substring(0, Math.min(20, computedHex.length())) + "...");
            } else {
                log.info("Casso signature verified OK");
            }
            return valid;
        } catch (Exception e) {
            log.error("Casso signature verification failed", e);
            return false;
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
