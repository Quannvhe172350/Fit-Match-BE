package com.fitmatch.controller;

import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.dto.gym.GymPolicyRequest;
import com.fitmatch.dto.gym.GymPolicyResponse;
import com.fitmatch.service.GymOperationsConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/gym/policies")
@RequiredArgsConstructor
@Tag(name = "G. Gym Policies", description = "Chính sách vận hành của Gym (UC-017). Yêu cầu ROLE_GYM_OPERATOR + Gym APPROVED.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('GYM_OPERATOR')")
public class GymPolicyController {

    private final GymOperationsConfigService service;

    @Operation(summary = "UC-017 — Cấu hình chính sách vận hành (upsert)",
            description = "Actor: **Gym Operator**. Chính sách đặt lịch, hủy, no-show, nội quy — nền tảng cho tính phí/hoàn tiền ở booking. Lỗi: 409 Gym chưa APPROVED.")
    @PutMapping
    public ResponseEntity<ApiResponse<GymPolicyResponse>> upsert(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody GymPolicyRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Gym policies saved",
                service.upsertPolicy(userDetails.getUsername(), request)));
    }

    @Operation(summary = "UC-017 — Xem chính sách vận hành",
            description = "Actor: **Gym Operator**. Lỗi: 404 chưa cấu hình.")
    @GetMapping
    public ResponseEntity<ApiResponse<GymPolicyResponse>> get(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success(service.getPolicy(userDetails.getUsername())));
    }
}
