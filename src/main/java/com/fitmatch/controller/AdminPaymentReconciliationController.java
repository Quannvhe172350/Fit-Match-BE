package com.fitmatch.controller;

import com.fitmatch.common.enums.PaymentTxnAnomaly;
import com.fitmatch.common.enums.ReconStatus;
import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.payment.PaymentTransactionResponse;
import com.fitmatch.dto.payment.ReconciliationApplyRequest;
import com.fitmatch.dto.payment.ReconciliationResolveRequest;
import com.fitmatch.dto.payment.ReconciliationSummaryResponse;
import com.fitmatch.service.PaymentReconciliationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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

@RestController
@RequestMapping("/api/admin/payments/reconciliation")
@RequiredArgsConstructor
@Tag(name = "H. Admin - Payment reconciliation",
        description = "Đối soát tiền vào không khớp booking (UC-053/056). Yêu cầu ROLE_ADMIN hoặc ROLE_FINANCE_ADMIN.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN','FINANCE_ADMIN')")
public class AdminPaymentReconciliationController {

    private final PaymentReconciliationService reconciliationService;

    @Operation(
            summary = "UC-053 — Hàng đợi giao dịch cần đối soát",
            description = "Actor: **Admin/Finance**. Mặc định trả các giao dịch NEEDS_REVIEW (tiền đã vào tài khoản nền tảng nhưng chưa gắn được vào booking). Truyền reconStatus rỗng để xem toàn bộ lịch sử.")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<PaymentTransactionResponse>>> list(
            @Parameter(description = "Trạng thái đối soát (mặc định NEEDS_REVIEW; bỏ trống = tất cả)")
            @RequestParam(required = false, defaultValue = "NEEDS_REVIEW") String reconStatus,
            @Parameter(description = "Loại bất thường (bỏ trống = tất cả)")
            @RequestParam(required = false) PaymentTxnAnomaly anomaly,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        ReconStatus status = reconStatus == null || reconStatus.isBlank()
                ? null : ReconStatus.valueOf(reconStatus);
        return ResponseEntity.ok(ApiResponse.success(
                reconciliationService.list(status, anomaly, pageable)));
    }

    @Operation(
            summary = "UC-053 — Tổng quan tiền treo chờ đối soát",
            description = "Actor: **Admin/Finance**. Số giao dịch và tổng tiền còn NEEDS_REVIEW, tách theo loại bất thường (UNMATCHED / UNDERPAID / OVERPAID / LATE_ARRIVAL / DUPLICATE).")
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<ReconciliationSummaryResponse>> summary() {
        return ResponseEntity.ok(ApiResponse.success(reconciliationService.summary()));
    }

    @Operation(
            summary = "UC-053/056 — Gắn giao dịch vào vé chờ thanh toán",
            description = "Actor: **Admin/Finance**. Dùng cho ca khách chuyển khoản sai nội dung: xác nhận giữ tiền cho vé như webhook thật (hold ví, đơn -> PAID, chuyển Gym, thông báo khách). Lỗi: 409 vé/đơn không còn chờ thanh toán hoặc giao dịch đã đối soát; 400 giao dịch không đủ tiền (bật allowAmountMismatch kèm ghi chú nếu khách chuyển nhiều lần).")
    @PostMapping("/{id}/apply")
    public ResponseEntity<ApiResponse<PaymentTransactionResponse>> apply(
            @PathVariable Long id,
            @Valid @RequestBody ReconciliationApplyRequest request,
            @AuthenticationPrincipal UserDetails actor) {
        return ResponseEntity.ok(ApiResponse.success("Transaction applied to ticket",
                reconciliationService.applyToTicket(id, request.getTicketId(),
                        request.isAllowAmountMismatch(), request.getNote(), actor.getUsername())));
    }

    @Operation(
            summary = "UC-056 — Tất toán giao dịch không gắn booking",
            description = "Actor: **Admin/Finance**. RESOLVED_REFUNDED = đã chuyển trả người gửi (thao tác ngân hàng ngoài hệ thống, ghi lại theo record này); RESOLVED_IGNORED = không cần hành động. Ghi chú bắt buộc. Lỗi: 409 giao dịch đã đối soát.")
    @PostMapping("/{id}/resolve")
    public ResponseEntity<ApiResponse<PaymentTransactionResponse>> resolve(
            @PathVariable Long id,
            @Valid @RequestBody ReconciliationResolveRequest request,
            @AuthenticationPrincipal UserDetails actor) {
        return ResponseEntity.ok(ApiResponse.success("Transaction reconciled",
                reconciliationService.resolve(id, request.getResolution(),
                        request.getNote(), actor.getUsername())));
    }
}
