package com.fitmatch.controller;

import com.fitmatch.common.enums.Role;
import com.fitmatch.common.enums.UserStatus;
import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.admin.AssignRoleRequest;
import com.fitmatch.dto.admin.UpdateUserStatusRequest;
import com.fitmatch.dto.user.UserResponse;
import com.fitmatch.service.AdminUserService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@Tag(name = "B. Admin - User Management", description = "Quản trị người dùng (UC-10 → UC-12). Yêu cầu ROLE_ADMIN.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @Operation(
            summary = "UC-10 — Tìm kiếm / lọc danh sách user",
            description = """
                    Actor: **Admin**. Tìm kiếm user theo keyword (username/email), role, status; có phân trang.
                    Lỗi: 401 chưa đăng nhập; 403 không phải Admin.
                    """)
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<UserResponse>>> search(
            @Parameter(description = "Từ khoá khớp username hoặc email") @RequestParam(required = false) String keyword,
            @Parameter(description = "Lọc theo role") @RequestParam(required = false) Role role,
            @Parameter(description = "Lọc theo trạng thái") @RequestParam(required = false) UserStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                adminUserService.searchUsers(keyword, role, status, pageable)));
    }

    @Operation(
            summary = "UC-10 — Xem chi tiết user",
            description = "Actor: **Admin**. Lấy chi tiết một user theo id. Lỗi: 404 không tồn tại; 403 không phải Admin.")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> detail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(adminUserService.getUserDetail(id)));
    }

    @Operation(
            summary = "UC-11 — Khoá / mở khoá tài khoản",
            description = """
                    Actor: **Admin**. Đổi trạng thái tài khoản: BANNED (khoá) hoặc ACTIVE (mở khoá).
                    Không thể đổi trạng thái chính tài khoản của mình. Hành động được ghi audit log.
                    Lỗi: 400 trùng trạng thái/tự tác động; 404 không tồn tại; 403 không phải Admin.
                    """)
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<UserResponse>> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserStatusRequest request,
            @AuthenticationPrincipal UserDetails actor) {
        UserResponse response = adminUserService.updateUserStatus(id, request.getStatus(), actor.getUsername());
        return ResponseEntity.ok(ApiResponse.success("User status updated", response));
    }

    @Operation(
            summary = "UC-12 — Gán role cho user",
            description = """
                    Actor: **Admin**. Gán role (ROLE_CUSTOMER/ROLE_PT/ROLE_GYM_OPERATOR/ROLE_ADMIN).
                    Không thể đổi role chính tài khoản của mình. Hành động được ghi audit log.
                    Lỗi: 400 trùng role/tự tác động; 404 không tồn tại; 403 không phải Admin.
                    """)
    @PatchMapping("/{id}/role")
    public ResponseEntity<ApiResponse<UserResponse>> assignRole(
            @PathVariable Long id,
            @Valid @RequestBody AssignRoleRequest request,
            @AuthenticationPrincipal UserDetails actor) {
        UserResponse response = adminUserService.assignRole(id, request.getRole(), actor.getUsername());
        return ResponseEntity.ok(ApiResponse.success("User role updated", response));
    }
}
