package com.fitmatch.controller;

import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.VerificationStatus;
import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.admin.RejectRequest;
import com.fitmatch.dto.pt.PtProfileResponse;
import com.fitmatch.service.AdminPtVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/pt-verifications")
@RequiredArgsConstructor
@Tag(name = "D. Admin - PT Verification [DEPRECATED]",
        description = "DEPRECATED — mô hình PT self-verification đã bỏ (UC-019 mới): Gym tạo và quản lý PT. Giữ tạm cho dữ liệu/client cũ. Yêu cầu ROLE_ADMIN.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
@Deprecated
public class AdminPtVerificationController {

    private final AdminPtVerificationService service;

    @Operation(
            deprecated = true,
            summary = "[DEPRECATED] Danh sách yêu cầu xác minh PT",
            description = "**DEPRECATED (UC-019).** Actor: **Admin**. Liệt kê hồ sơ PT theo trạng thái (mặc định PENDING), có phân trang. Read-only.")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<PtProfileResponse>>> list(
            @Parameter(description = "Trạng thái xác minh (mặc định PENDING)")
            @RequestParam(required = false) VerificationStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(service.list(status, pageable)));
    }

    @Operation(
            deprecated = true,
            summary = "[DEPRECATED] Chi tiết yêu cầu xác minh PT",
            description = "**DEPRECATED (UC-019).** Actor: **Admin**. Xem chi tiết hồ sơ PT kèm tài liệu. Lỗi: 404 không tồn tại.")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PtProfileResponse>> detail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(service.detail(id)));
    }

    @Operation(
            deprecated = true,
            summary = "[DISABLED] Duyệt xác minh PT",
            description = "**410 GONE (UC-019) — PT không còn qua platform verification.** Approve luồng cũ sẽ promote user lên ROLE_PT không qua Gym — mâu thuẫn mô hình mới nên bị chặn cứng.")
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<PtProfileResponse>> approve(
            @PathVariable Long id, @AuthenticationPrincipal UserDetails actor) {
        throw new BusinessException(ErrorCode.LEGACY_ENDPOINT_DISABLED);
    }

    @Operation(
            deprecated = true,
            summary = "[DISABLED] Từ chối xác minh PT",
            description = "**410 GONE (UC-019) — PT không còn qua platform verification.**")
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<PtProfileResponse>> reject(
            @PathVariable Long id,
            @Valid @RequestBody RejectRequest request,
            @AuthenticationPrincipal UserDetails actor) {
        throw new BusinessException(ErrorCode.LEGACY_ENDPOINT_DISABLED);
    }
}
