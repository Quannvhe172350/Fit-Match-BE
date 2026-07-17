package com.fitmatch.controller;

import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.dto.admin.RejectRequest;
import com.fitmatch.dto.pt.GymPtResponse;
import com.fitmatch.service.AdminPtManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/pts")
@RequiredArgsConstructor
@Tag(name = "D. Admin - PT Management",
        description = "Admin can thiệp trạng thái PT khi có sự cố chất lượng/an toàn (UC-021). Yêu cầu ROLE_ADMIN.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPtController {

    private final AdminPtManagementService service;

    @Operation(
            summary = "UC-021 — Đình chỉ PT",
            description = "Actor: **Admin**. `{userId}` là **User.id** của tài khoản PT (khớp trang quản lý user), KHÔNG phải PtProfile.id. Đình chỉ PT (sự cố chất lượng/an toàn) -> SUSPENDED, ẩn khỏi marketplace, không nhận booking; Gym không tự gỡ được. Lỗi: 409 đã SUSPENDED; 404 user không có hồ sơ PT.")
    @PostMapping("/{userId}/suspend")
    public ResponseEntity<ApiResponse<GymPtResponse>> suspend(
            @PathVariable Long userId,
            @Valid @RequestBody RejectRequest request,
            @AuthenticationPrincipal UserDetails actor) {
        return ResponseEntity.ok(ApiResponse.success("PT suspended",
                service.suspend(userId, request.getReason(), actor.getUsername())));
    }

    @Operation(
            summary = "UC-021 — Gỡ đình chỉ PT",
            description = "Actor: **Admin**. `{userId}` là **User.id** của tài khoản PT. SUSPENDED -> ACTIVE. Lỗi: 409 không ở SUSPENDED; 404 user không có hồ sơ PT.")
    @PostMapping("/{userId}/reactivate")
    public ResponseEntity<ApiResponse<GymPtResponse>> reactivate(
            @PathVariable Long userId, @AuthenticationPrincipal UserDetails actor) {
        return ResponseEntity.ok(ApiResponse.success("PT reactivated",
                service.reactivate(userId, actor.getUsername())));
    }
}
