package com.fitmatch.controller;

import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.dto.booking.BookingResponse;
import com.fitmatch.dto.booking.CancelBookingRequest;
import com.fitmatch.dto.booking.CreateBookingRequest;
import com.fitmatch.dto.booking.RescheduleBookingRequest;
import com.fitmatch.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Tag(name = "E. Bookings", description = "Đặt lịch dịch vụ/gói tập (UC-031 → UC-045). Yêu cầu ROLE_CUSTOMER.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('CUSTOMER')")
public class BookingController {

    private final BookingService bookingService;

    @Operation(
            summary = "UC-031 — Tạo booking nháp",
            description = "Actor: **Customer**. Tạo booking DRAFT với tối thiểu một đích (serviceId/packageId/branchId/ptId) để xác định Gym chịu trách nhiệm; mọi đích phải thuộc cùng một Gym. Lỗi: 400 thiếu/lệch Gym; 404 đích không tồn tại.")
    @PostMapping
    public ResponseEntity<ApiResponse<BookingResponse>> create(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CreateBookingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Booking draft created",
                bookingService.createDraft(userDetails.getUsername(), request)));
    }

    @Operation(
            summary = "UC-035 — Checkout booking",
            description = "Actor: **Customer**. Validate đủ điều kiện (UC-033: gym/catalog/lịch/PT/capacity), chốt giá (UC-034) và chuyển DRAFT -> PENDING_PAYMENT (miễn phí -> PENDING_GYM). Lỗi: 409 không đủ điều kiện kèm danh sách lý do; 404 không thuộc về bạn.")
    @PostMapping("/{id}/checkout")
    public ResponseEntity<ApiResponse<BookingResponse>> checkout(
            @AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Booking checked out",
                bookingService.checkout(userDetails.getUsername(), id)));
    }

    @Operation(
            summary = "UC-041 — Dời lịch booking",
            description = "Actor: **Customer**. Dời PENDING_GYM/CONFIRMED sang khung giờ mới hợp lệ (PT rảnh, branch mở, còn chỗ). Lỗi: 409 sai trạng thái hoặc khung giờ vi phạm; 404 không thuộc về bạn.")
    @PostMapping("/{id}/reschedule")
    public ResponseEntity<ApiResponse<BookingResponse>> reschedule(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody RescheduleBookingRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Booking rescheduled",
                bookingService.reschedule(userDetails.getUsername(), id, request.getStartAt(), request.getEndAt())));
    }

    @Operation(
            summary = "UC-042 — Hủy booking",
            description = "Actor: **Customer**. Hủy DRAFT/PENDING_PAYMENT/PENDING_GYM/CONFIRMED; hủy CONFIRMED trong cửa sổ mất phí bị đánh dấu lateCancellation (ảnh hưởng hoàn tiền — UC-043). Lỗi: 409 trạng thái cuối; 404 không thuộc về bạn.")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<BookingResponse>> cancel(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @RequestBody(required = false) CancelBookingRequest request) {
        String reason = request != null ? request.getReason() : null;
        return ResponseEntity.ok(ApiResponse.success("Booking cancelled",
                bookingService.cancel(userDetails.getUsername(), id, reason)));
    }

    @Operation(
            summary = "UC-032 — Cập nhật lựa chọn của booking nháp",
            description = "Actor: **Customer**. Đổi service/package/PT/branch/khung giờ khi booking còn DRAFT; field null giữ nguyên. Lỗi: 409 không ở DRAFT; 400 lựa chọn khác Gym; 404 không thuộc về bạn.")
    @PutMapping("/{id}/selection")
    public ResponseEntity<ApiResponse<BookingResponse>> updateSelection(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody CreateBookingRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Booking selection updated",
                bookingService.updateSelection(userDetails.getUsername(), id, request)));
    }
}
