package com.fitmatch.controller;

import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.notification.NotificationResponse;
import com.fitmatch.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "J. Notifications", description = "Hộp thư thông báo in-app theo sự kiện (UC-075). Actor: người dùng đã đăng nhập.")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "UC-075 — Danh sách thông báo của tôi",
            description = "Actor: **Authenticated user**. Mới nhất trước, phân trang.")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<NotificationResponse>>> list(
            @AuthenticationPrincipal UserDetails userDetails,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                notificationService.list(userDetails.getUsername(), pageable)));
    }

    @Operation(summary = "UC-075 — Số thông báo chưa đọc",
            description = "Actor: **Authenticated user**. Dùng cho badge chuông.")
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> unreadCount(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("count", notificationService.unreadCount(userDetails.getUsername()))));
    }

    @Operation(summary = "UC-075 — Đánh dấu đã đọc một thông báo",
            description = "Actor: **Authenticated user**. Lỗi: 404 không thuộc về bạn.")
    @PostMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markRead(
            @AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        notificationService.markRead(userDetails.getUsername(), id);
        return ResponseEntity.ok(ApiResponse.success("Marked as read", null));
    }

    @Operation(summary = "UC-075 — Đánh dấu tất cả đã đọc",
            description = "Actor: **Authenticated user**.")
    @PostMapping("/read-all")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> markAllRead(
            @AuthenticationPrincipal UserDetails userDetails) {
        int updated = notificationService.markAllRead(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success("All marked as read", Map.of("updated", updated)));
    }
}
