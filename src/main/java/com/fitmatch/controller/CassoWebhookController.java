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
import java.util.Base64;
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

        // Secret rỗng/chưa cấu hình -> từ chối tất cả (fail-safe).
        if (secret == null || secret.isBlank() || signature == null || signature.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid webhook signature");
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
            String payload;
            if (rawBody != null) {
                payload = rawBody.toString();
            } else {
                log.warn("RAW_BODY attribute not set — ensure WebhookBodyCachingFilter is configured");
                return false;
            }

            String data = timestamp + "." + payload;
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] computed = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            String computedBase64 = Base64.getEncoder().encodeToString(computed);

            boolean valid = java.security.MessageDigest.isEqual(
                    expectedHmac.getBytes(StandardCharsets.UTF_8),
                    computedBase64.getBytes(StandardCharsets.UTF_8));
            if (!valid) {
                log.warn("Casso signature mismatch");
            }
            return valid;
        } catch (Exception e) {
            log.error("Casso signature verification failed", e);
            return false;
        }
    }
}
