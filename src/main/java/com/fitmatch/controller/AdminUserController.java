package com.fitmatch.controller;

import com.fitmatch.common.enums.Role;
import com.fitmatch.common.enums.UserStatus;
import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.user.UserResponse;
import com.fitmatch.service.AdminUserService;
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
}
