package com.fitmatch.controller;

import com.fitmatch.common.enums.VerificationStatus;
import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.pt.PtProfileResponse;
import com.fitmatch.service.AdminPtVerificationService;
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
@RequestMapping("/api/admin/pt-verifications")
@RequiredArgsConstructor
@Tag(name = "D. Admin - PT Verification", description = "Duyệt xác minh PT (UC-29, UC-30). Yêu cầu ROLE_ADMIN.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPtVerificationController {

    private final AdminPtVerificationService service;

    @Operation(
            summary = "UC-29 — Danh sách yêu cầu xác minh PT",
            description = "Actor: **Admin**. Liệt kê hồ sơ PT theo trạng thái (mặc định PENDING), có phân trang. Read-only.")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<PtProfileResponse>>> list(
            @Parameter(description = "Trạng thái xác minh (mặc định PENDING)")
            @RequestParam(required = false) VerificationStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(service.list(status, pageable)));
    }

    @Operation(
            summary = "UC-29 — Chi tiết yêu cầu xác minh PT",
            description = "Actor: **Admin**. Xem chi tiết hồ sơ PT kèm tài liệu. Lỗi: 404 không tồn tại.")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PtProfileResponse>> detail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(service.detail(id)));
    }
}
