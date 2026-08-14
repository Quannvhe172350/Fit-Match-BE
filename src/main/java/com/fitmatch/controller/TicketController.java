package com.fitmatch.controller;

import com.fitmatch.common.enums.TicketStatus;
import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.payment.PaymentOrderResponse;
import com.fitmatch.dto.ticket.ScheduleTicketRequest;
import com.fitmatch.dto.ticket.TicketPurchaseResponse;
import com.fitmatch.dto.ticket.TicketQuoteRequest;
import com.fitmatch.dto.ticket.TicketQuoteResponse;
import com.fitmatch.dto.ticket.TicketResponse;
import com.fitmatch.dto.ticket.TicketStatusHistoryResponse;
import com.fitmatch.service.TicketPurchaseService;
import com.fitmatch.service.TicketSchedulingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
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

import java.util.List;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
@Tag(name = "C. Tickets", description = "Mua vé và quản lý vé của khách")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('CUSTOMER')")
public class TicketController {

    private final TicketPurchaseService purchaseService;
    private final TicketSchedulingService schedulingService;

    @Operation(summary = "Báo giá vé trước khi mua",
            description = "Actor: **Customer**. Chỉ đọc — không tạo vé, không tiêu voucher/điểm. "
                    + "Mã voucher sai KHÔNG gây lỗi, chỉ trả voucherMessage để FE hiện dưới ô nhập.")
    @PostMapping("/quote")
    public ResponseEntity<ApiResponse<TicketQuoteResponse>> quote(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody TicketQuoteRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                purchaseService.quote(userDetails.getUsername(), request)));
    }

    @Operation(summary = "Mua vé",
            description = "Actor: **Customer**. payableAmount == 0 (điểm/voucher phủ hết) thì vé "
                    + "ACTIVE ngay và paymentOrder = null; ngược lại vé PENDING_PAYMENT kèm QR VietQR. "
                    + "Lỗi: 409 vé ngừng bán / không bán ở chi nhánh đã chọn / voucher hết hiệu lực.")
    @PostMapping("/purchase")
    public ResponseEntity<ApiResponse<TicketPurchaseResponse>> purchase(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody TicketQuoteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Ticket purchased",
                purchaseService.purchase(userDetails.getUsername(), request)));
    }

    @Operation(summary = "Vé của tôi", description = "Actor: **Customer**. Lọc theo status nếu cần.")
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<PageResponse<TicketResponse>>> myTickets(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) TicketStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                purchaseService.myTickets(userDetails.getUsername(), status, pageable)));
    }

    @Operation(summary = "Chi tiết vé kèm danh sách ngày tập", description = "Actor: **Customer** (chủ vé).")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TicketResponse>> detail(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                purchaseService.detail(userDetails.getUsername(), id)));
    }

    @Operation(summary = "Đơn thanh toán của vé",
            description = "Actor: **Customer** (chủ vé). FE poll 5s ở màn QR cho tới khi status = PAID.")
    @GetMapping("/{id}/payment")
    public ResponseEntity<ApiResponse<PaymentOrderResponse>> payment(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                purchaseService.payment(userDetails.getUsername(), id)));
    }

    @Operation(summary = "Lịch sử trạng thái vé", description = "Actor: **Customer** (chủ vé).")
    @GetMapping("/{id}/history")
    public ResponseEntity<ApiResponse<List<TicketStatusHistoryResponse>>> history(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                purchaseService.history(userDetails.getUsername(), id)));
    }

    @Operation(summary = "Huỷ vé chưa thanh toán",
            description = "Actor: **Customer** (chủ vé). Chỉ áp dụng cho vé PENDING_PAYMENT — "
                    + "vé đã kích hoạt phải đi đường yêu cầu hoàn tiền.")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<TicketResponse>> cancelUnpaid(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Ticket cancelled",
                purchaseService.cancelUnpaid(userDetails.getUsername(), id)));
    }

    @Operation(summary = "Đặt lịch cho vé",
            description = "Actor: **Customer** (chủ vé). Vé DAY gửi {date, ptId?, slotStart?}. "
                    + "Vé PACKAGE gửi {startDate, days[]} — server sinh đủ dayCount ngày LIÊN TIẾP; "
                    + "ngày nào không khai PT thì để trống, bổ sung sau.")
    @PostMapping("/{id}/schedule")
    public ResponseEntity<ApiResponse<TicketResponse>> schedule(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody ScheduleTicketRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Sessions scheduled",
                schedulingService.schedule(userDetails.getUsername(), id, request)));
    }
}
