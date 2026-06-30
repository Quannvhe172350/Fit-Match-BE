package com.fitmatch.controller;

import com.fitmatch.common.enums.VerificationStatus;
import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.gym.GymProfileResponse;
import com.fitmatch.service.AdminGymVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/gym-verifications")
@RequiredArgsConstructor
@Tag(name = "F. Admin - Gym Verification", description = "Duyệt xác minh Gym (UC-45, UC-46). Yêu cầu ROLE_ADMIN.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminGymVerificationController {

    private final AdminGymVerificationService service;

    @Operation(
            summary = "UC-45 — Danh sách yêu cầu xác minh Gym",
            description = "Actor: **Admin**. Liệt kê hồ sơ Gym theo trạng thái (mặc định PENDING), phân trang. Read-only.")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<GymProfileResponse>>> list(
            @Parameter(description = "Trạng thái xác minh (mặc định PENDING)")
            @RequestParam(required = false) VerificationStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(service.list(status, pageable)));
    }

    @Operation(
            summary = "UC-45 — Chi tiết yêu cầu xác minh Gym",
            description = "Actor: **Admin**. Xem chi tiết hồ sơ Gym kèm tài liệu. Lỗi: 404 không tồn tại.")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<GymProfileResponse>> detail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(service.detail(id)));
    }
}
