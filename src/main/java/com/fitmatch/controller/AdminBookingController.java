package com.fitmatch.controller;

import com.fitmatch.common.enums.BookingStatus;
import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.booking.BookingResponse;
import com.fitmatch.dto.booking.BookingStatusHistoryResponse;
import com.fitmatch.service.AdminBookingService;
import com.fitmatch.service.BookingQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/bookings")
@RequiredArgsConstructor
@Tag(name = "E. Admin - Bookings", description = "Thao tác booking cấp quản trị (UC-036, UC-045). Yêu cầu ROLE_ADMIN.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminBookingController {

    private final AdminBookingService adminBookingService;
    private final BookingQueryService bookingQueryService;

    @Operation(
            summary = "UC-045 — Danh sách booking toàn hệ thống",
            description = "Actor: **Admin**. Lọc theo trạng thái, phân trang.")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<BookingResponse>>> list(
            @RequestParam(required = false) BookingStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(bookingQueryService.adminList(status, pageable)));
    }

    @Operation(
            summary = "UC-045 — Chi tiết booking (Admin)",
            description = "Actor: **Admin**. Lỗi: 404 không tồn tại.")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BookingResponse>> detail(
            @AuthenticationPrincipal UserDetails actor, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                bookingQueryService.detail(actor.getUsername(), id)));
    }

    @Operation(
            summary = "UC-040 — Timeline trạng thái booking (Admin)",
            description = "Actor: **Admin**. Lỗi: 404 không tồn tại.")
    @GetMapping("/{id}/history")
    public ResponseEntity<ApiResponse<java.util.List<BookingStatusHistoryResponse>>> history(
            @AuthenticationPrincipal UserDetails actor, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                bookingQueryService.history(actor.getUsername(), id)));
    }

    @Operation(
            summary = "UC-036 — Xác nhận đã giữ tiền booking",
            description = "Actor: **Admin**. PENDING_PAYMENT -> PENDING_GYM. Tạm thời thao tác tay; khi tích hợp VietQR/Casso sẽ được webhook đối soát gọi tự động. Lỗi: 409 sai trạng thái; 404 không tồn tại.")
    @PostMapping("/{id}/confirm-payment")
    public ResponseEntity<ApiResponse<BookingResponse>> confirmPayment(
            @PathVariable Long id, @AuthenticationPrincipal UserDetails actor) {
        return ResponseEntity.ok(ApiResponse.success("Payment hold confirmed",
                adminBookingService.confirmPaymentHold(id, actor.getUsername())));
    }
}
