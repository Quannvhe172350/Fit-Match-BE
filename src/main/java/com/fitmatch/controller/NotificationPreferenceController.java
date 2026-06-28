package com.fitmatch.controller;

import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.dto.user.NotificationPreferenceResponse;
import com.fitmatch.dto.user.UpdateNotificationPreferenceRequest;
import com.fitmatch.service.NotificationPreferenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user/notification-preferences")
@RequiredArgsConstructor
@Tag(name = "A. Notification Preferences", description = "Quản lý tùy chọn nhận thông báo (UC-07)")
@SecurityRequirement(name = "bearerAuth")
public class NotificationPreferenceController {

    private final NotificationPreferenceService preferenceService;

    @Operation(
            summary = "UC-07 — Xem tùy chọn thông báo",
            description = "Actor: **Authenticated**. Trả về tùy chọn thông báo; tự tạo mặc định nếu người dùng chưa cấu hình.")
    @GetMapping
    public ResponseEntity<ApiResponse<NotificationPreferenceResponse>> get(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success(preferenceService.getPreferences(userDetails.getUsername())));
    }

    @Operation(
            summary = "UC-07 — Cập nhật tùy chọn thông báo",
            description = "Actor: **Authenticated**. Cập nhật một phần (trường null = giữ nguyên).")
    @PutMapping
    public ResponseEntity<ApiResponse<NotificationPreferenceResponse>> update(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody UpdateNotificationPreferenceRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Notification preferences updated",
                preferenceService.updatePreferences(userDetails.getUsername(), request)));
    }
}
