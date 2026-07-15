package com.fitmatch.controller;

import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.dto.loyalty.LoyaltyBalanceResponse;
import com.fitmatch.service.LoyaltyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/loyalty")
@RequiredArgsConstructor
@Tag(name = "M. Loyalty", description = "Điểm thưởng của khách (UC-073). Yêu cầu ROLE_CUSTOMER.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('CUSTOMER')")
public class LoyaltyController {

    private final LoyaltyService loyaltyService;

    @Operation(
            summary = "UC-073 — Số dư điểm & lịch sử",
            description = "Actor: **Customer**. Trả số dư điểm, tỷ lệ quy đổi và lịch sử tích/tiêu điểm.")
    @GetMapping
    public ResponseEntity<ApiResponse<LoyaltyBalanceResponse>> balance(
            @AuthenticationPrincipal UserDetails userDetails,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                loyaltyService.balance(userDetails.getUsername(), pageable)));
    }
}
