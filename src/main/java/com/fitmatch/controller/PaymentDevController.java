package com.fitmatch.controller;

import com.fitmatch.common.enums.BookingStatus;
import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.PaymentStatus;
import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.dto.payment.PaymentOrderResponse;
import com.fitmatch.entity.PaymentOrder;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.PaymentOrderRepository;
import com.fitmatch.service.support.BookingPaymentHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * Bug 8 (UC-053, CHỈ dev/local): môi trường phát triển không có Casso bắn
 * webhook (webhook-secret rỗng -> mọi call bị chặn), nên khách kẹt vĩnh viễn ở
 * "Chờ thanh toán" khi test luồng gói tháng. Endpoint này mô phỏng ngân hàng
 * xác nhận chuyển khoản: đánh dấu đơn PAID và chạy đúng luồng giữ tiền/notify
 * như webhook thật. KHÔNG được kích hoạt ở prod (gated bằng @Profile).
 */
@Slf4j
@RestController
@RequestMapping("/api/payments/dev")
@RequiredArgsConstructor
@Profile({"local", "dev"})
@PreAuthorize("hasRole('CUSTOMER')")
@Tag(name = "Z. Dev - Payment simulator", description = "Mô phỏng thanh toán VietQR (chỉ profile local/dev)")
public class PaymentDevController {

    private final PaymentOrderRepository paymentOrderRepository;
    private final BookingPaymentHandler bookingPaymentHandler;

    @Operation(
            summary = "Dev — Mô phỏng thanh toán thành công",
            description = "Actor: **Customer** (chủ booking). Đánh dấu đơn VietQR PAID và chuyển booking sang PENDING_GYM như webhook Casso thật. Chỉ tồn tại ở profile local/dev.")
    @PostMapping("/{bookingId}/simulate")
    @Transactional
    public ResponseEntity<ApiResponse<PaymentOrderResponse>> simulate(
            @PathVariable Long bookingId,
            @AuthenticationPrincipal UserDetails userDetails) {
        PaymentOrder order = paymentOrderRepository
                .findByBooking_IdAndBooking_Customer_Username(bookingId, userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("Payment order for booking", bookingId));
        if (order.getStatus() != PaymentStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Đơn thanh toán không còn ở trạng thái chờ (hiện tại: " + order.getStatus() + ")");
        }
        if (order.getExpiresAt() != null && order.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Đơn thanh toán đã hết hạn");
        }

        order.setStatus(PaymentStatus.PAID);
        order.setPaidAt(LocalDateTime.now());
        paymentOrderRepository.save(order);
        if (order.getBooking().getStatus() == BookingStatus.PENDING_PAYMENT) {
            bookingPaymentHandler.onPaymentConfirmed(order.getBooking(), order.getAmount(), "dev-simulator");
        }
        log.warn("DEV payment simulator: booking {} confirmed by {}", bookingId, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success(PaymentOrderResponse.of(order)));
    }
}
