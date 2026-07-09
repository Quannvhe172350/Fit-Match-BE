package com.fitmatch.controller;

import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.dto.admin.CommissionConfigRequest;
import com.fitmatch.dto.admin.CommissionConfigResponse;
import com.fitmatch.service.CommissionConfigService;
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
@RequestMapping("/api/admin/commission-config")
@RequiredArgsConstructor
@Tag(name = "H. Admin - Commission Config",
        description = "Cấu hình hoa hồng, phí nền tảng và holding period (UC-072). Yêu cầu ROLE_ADMIN hoặc ROLE_FINANCE_ADMIN.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN','FINANCE_ADMIN')")
public class AdminCommissionController {

    private final CommissionConfigService commissionConfigService;

    @Operation(summary = "UC-072 — Xem cấu hình kinh tế nền tảng",
            description = "Actor: **Admin/Finance Admin**. Trả về hoa hồng %, phí nền tảng %, số ngày giữ tiền (mặc định 15/0/3 nếu chưa cấu hình).")
    @GetMapping
    public ResponseEntity<ApiResponse<CommissionConfigResponse>> get() {
        return ResponseEntity.ok(ApiResponse.success(commissionConfigService.get()));
    }

    @Operation(summary = "UC-072 — Cập nhật cấu hình kinh tế nền tảng",
            description = "Actor: **Admin/Finance Admin**. Tạo bản cấu hình mới (giữ lịch sử). Lỗi: 400 % ngoài 0-100.")
    @PutMapping
    public ResponseEntity<ApiResponse<CommissionConfigResponse>> update(
            @Valid @RequestBody CommissionConfigRequest request,
            @AuthenticationPrincipal UserDetails actor) {
        return ResponseEntity.ok(ApiResponse.success("Commission config updated",
                commissionConfigService.update(request, actor.getUsername())));
    }
}
