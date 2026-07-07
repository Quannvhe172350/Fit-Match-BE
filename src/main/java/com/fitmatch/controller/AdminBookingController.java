package com.fitmatch.controller;

import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.dto.booking.BookingResponse;
import com.fitmatch.service.AdminBookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/bookings")
@RequiredArgsConstructor
@Tag(name = "E. Admin - Bookings", description = "Thao tác booking cấp quản trị (UC-036, UC-045). Yêu cầu ROLE_ADMIN.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminBookingController {

    private final AdminBookingService adminBookingService;

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
