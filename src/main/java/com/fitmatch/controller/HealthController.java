package com.fitmatch.controller;

import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.service.HealthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
@Tag(name = "Z. System", description = "Kiểm tra trạng thái hệ thống")
public class HealthController {

    private final HealthService healthService;

    @Operation(
            summary = "Health check",
            description = "Actor: **Guest**. Trả về trạng thái hoạt động của server. Dùng cho load balancer / uptime monitoring. "
                    + "`paymentSimulatorEnabled` (BUG-11) cho FE biết endpoint mô phỏng thanh toán dev có thật sự "
                    + "tồn tại hay không — ở prod luôn là false.")
    @SecurityRequirements // public
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "status", healthService.getStatus(),
                "paymentSimulatorEnabled", healthService.isPaymentSimulatorEnabled())));
    }
}
