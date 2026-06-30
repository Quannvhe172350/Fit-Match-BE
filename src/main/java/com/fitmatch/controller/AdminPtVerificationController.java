package com.fitmatch.controller;

import com.fitmatch.common.enums.VerificationStatus;
import com.fitmatch.common.response.ApiResponse;
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

    @Operation(
            summary = "UC-30 — Duyệt xác minh PT",
            description = """
                    Actor: **Admin**. Duyệt hồ sơ PENDING -> APPROVED, kích hoạt hiển thị marketplace và
                    nâng tài khoản lên ROLE_PT. Lỗi: 409 nếu không ở trạng thái PENDING; 404 không tồn tại.
                    """)
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<PtProfileResponse>> approve(
            @PathVariable Long id, @AuthenticationPrincipal UserDetails actor) {
        return ResponseEntity.ok(ApiResponse.success("PT verification approved",
                service.approve(id, actor.getUsername())));
    }

    @Operation(
            summary = "UC-30 — Từ chối xác minh PT",
            description = "Actor: **Admin**. Từ chối hồ sơ PENDING -> REJECTED kèm lý do (PT có thể nộp lại - UC-25). Lỗi: 409 không ở PENDING; 404 không tồn tại.")
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<PtProfileResponse>> reject(
            @PathVariable Long id,
            @Valid @RequestBody RejectRequest request,
            @AuthenticationPrincipal UserDetails actor) {
        return ResponseEntity.ok(ApiResponse.success("PT verification rejected",
                service.reject(id, request.getReason(), actor.getUsername())));
    }
}
