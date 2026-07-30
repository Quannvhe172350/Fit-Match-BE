package com.fitmatch.controller;

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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/webhooks/casso")
@RequiredArgsConstructor
@Tag(name = "E. Payment Webhook", description = "Webhook đối soát Casso (UC-053). Xác thực bằng header X-Casso-Signature (HMAC-SHA256).")
@SecurityRequirements
public class CassoWebhookController {

    private final PaymentWebhookService paymentWebhookService;
    private final PaymentProperties paymentProperties;

    @Operation(
            summary = "UC-053 — Nhận webhook đối soát Casso",
            description = """
                    Actor: **Casso (System)**. Xác thực bằng header `X-Casso-Signature`
                    (định dạng `t=<unix_millis>,v1=<base64_hmac>`) — HMAC-SHA256
                    của `timestamp + "." + rawBody` với key `app.casso.webhook-secret`.
                    Idempotent theo id giao dịch Casso.""")
    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> receive(
            @RequestBody CassoWebhookRequest request,
            HttpServletRequest httpRequest) {

        String secret = paymentProperties.getCasso().getWebhookSecret();
        String signature = httpRequest.getHeader("X-Casso-Signature");

        log.info("Casso webhook received: X-Casso-Signature={}, secretConfigured={}",
                signature != null ? signature.substring(0, Math.min(30, signature.length())) + "..." : "MISSING",
                secret != null && !secret.isBlank());

        // Secret rỗng/chưa cấu hình -> từ chối tất cả (fail-safe).
        if (secret == null || secret.isBlank()) {
            log.warn("Casso webhook rejected: CASSO_WEBHOOK_SECRET is not configured");
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Webhook secret not configured");
        }
        if (signature == null || signature.isBlank()) {
            log.warn("Casso webhook rejected: X-Casso-Signature header missing");
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Missing X-Casso-Signature header");
        }

        if (!verifySignature(signature, secret, httpRequest)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid webhook signature");
        }

        int matched = paymentWebhookService.processCasso(request);
        return ResponseEntity.ok(ApiResponse.success("Webhook processed",
                Map.of("matched", matched)));
    }

    /**
     * Xác thực chữ ký Casso V2: HMAC-SHA256(timestamp + "." + rawBody, secret).
     * <p>
     * Header {@code X-Casso-Signature} có định dạng {@code t=<unix_millis>,v1=<base64_hmac>}.
     * Raw body được lấy từ attribute {@code RAW_BODY} do {@code WebhookBodyCachingFilter} set.
     */
    private boolean verifySignature(String signatureHeader, String secret, HttpServletRequest request) {
        try {
            // Parse: "t=1727948258788,v1=ed0a4bd2..."
            String[] parts = signatureHeader.split(",");
            if (parts.length < 2) return false;

            String tPart = parts[0].trim();
            String v1Part = parts[1].trim();
            if (!tPart.startsWith("t=") || !v1Part.startsWith("v1=")) return false;

            String timestamp = tPart.substring(2);
            String expectedHmac = v1Part.substring(3);

            // Lấy raw body từ attribute (do WebhookBodyCachingFilter set)
            Object rawBody = request.getAttribute("RAW_BODY");
            if (rawBody == null) {
                log.warn("Casso signature verification failed: RAW_BODY attribute not set. "
                        + "Ensure WebhookBodyCachingFilter is before JwtAuthFilter in SecurityConfig.");
                return false;
            }
            String payload = rawBody.toString();

            String data = timestamp + "." + payload;
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] computed = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            String computedHex = bytesToHex(computed);

            boolean valid = java.security.MessageDigest.isEqual(
                    expectedHmac.getBytes(StandardCharsets.UTF_8),
                    computedHex.getBytes(StandardCharsets.UTF_8));
            if (!valid) {
                log.warn("Casso signature mismatch: timestamp={}, payloadLen={}, expectedHmac={}, computedHmac={}",
                        timestamp, payload.length(), expectedHmac.substring(0, Math.min(20, expectedHmac.length())) + "...",
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
