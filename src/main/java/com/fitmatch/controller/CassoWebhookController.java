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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/webhooks/casso")
@RequiredArgsConstructor
@Tag(name = "E. Payment Webhook", description = "Webhook đối soát Casso (UC-053). Xác thực bằng header Secure-Token.")
@SecurityRequirements
public class CassoWebhookController {

    private final PaymentWebhookService paymentWebhookService;
    private final PaymentProperties paymentProperties;

    @Operation(
            summary = "UC-053 — Nhận webhook đối soát Casso",
            description = "Actor: **Casso (System)**. Xác thực header Secure-Token = app.casso.webhook-secret; đối soát refCode + số tiền, ghi nhận thanh toán, giữ tiền vào ví và chuyển booking cho Gym. Idempotent theo id giao dịch Casso.")
    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> receive(
            @RequestHeader(value = "Secure-Token", required = false) String secureToken,
            @RequestBody CassoWebhookRequest request) {
        String expected = paymentProperties.getCasso().getWebhookSecret();
        // Secret rỗng/chưa cấu hình -> từ chối tất cả (fail-safe).
        // So sánh constant-time (MessageDigest.isEqual) chống timing side-channel.
        if (expected == null || expected.isBlank() || secureToken == null
                || !java.security.MessageDigest.isEqual(
                        expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        secureToken.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid webhook token");
        }
        int matched = paymentWebhookService.processCasso(request);
        return ResponseEntity.ok(ApiResponse.success("Webhook processed",
                Map.of("matched", matched)));
    }
}
