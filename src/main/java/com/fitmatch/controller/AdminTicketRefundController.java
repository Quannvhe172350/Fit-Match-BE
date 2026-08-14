package com.fitmatch.controller;

import com.fitmatch.common.enums.RefundStatus;
import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.payment.RefundResponse;
import com.fitmatch.dto.ticket.ApproveTicketRefundRequest;
import com.fitmatch.dto.ticket.TicketRefundPreviewResponse;
import com.fitmatch.service.TicketRefundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Pageable;
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

/**
 * Câu 11: hàng đợi duyệt hoàn tiền vé. Admin KHÔNG gõ số tiền — chỉ chọn FULL
 * hoặc PARTIAL_ELAPSED, số tiền do PartialRefundCalculator quyết để bất biến
 * "refund + retained = payable" không bị người dùng làm vỡ.
 */
@RestController
@RequestMapping("/api/admin/ticket-refunds")
@RequiredArgsConstructor
@Tag(name = "A. Admin Ticket Refunds", description = "Duyệt hoàn tiền vé")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN', 'FINANCE_ADMIN')")
public class AdminTicketRefundController {

    private final TicketRefundService refundService;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RejectRequest {
        @NotBlank(message = "note is required")
        @Size(max = 500)
        private String note;
    }

    @Operation(summary = "Hàng đợi yêu cầu hoàn tiền",
            description = "Actor: **Admin/Finance**. Không truyền status = không lọc.")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<RefundResponse>>> list(
            @RequestParam(required = false) RefundStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(refundService.listForAdmin(status, pageable)));
    }

    @Operation(summary = "Preview mức hoàn",
            description = "Actor: **Admin/Finance**. Trả cả hai lựa chọn kèm số tiền thật, số ngày "
                    + "đã qua, và số buổi tương lai sẽ bị huỷ nếu duyệt (câu 12). "
                    + "fullRefundOnly = true nghĩa là vé chưa dùng ngày nào, chỉ còn một lựa chọn (câu 13).")
    @GetMapping("/{id}/preview")
    public ResponseEntity<ApiResponse<TicketRefundPreviewResponse>> preview(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(refundService.preview(id)));
    }

    @Operation(summary = "Duyệt hoàn tiền",
            description = "Actor: **Admin/Finance**. mode = FULL | PARTIAL_ELAPSED. Duyệt sẽ HUỶ "
                    + "toàn bộ buổi tập tương lai của vé. Điểm thưởng/voucher chỉ được hoàn khi "
                    + "mode = FULL — hoàn một phần nghĩa là dịch vụ đã dùng một phần.")
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<RefundResponse>> approve(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody ApproveTicketRefundRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Refund approved",
                refundService.approve(id, request, userDetails.getUsername())));
    }

    @Operation(summary = "Từ chối hoàn tiền",
            description = "Actor: **Admin/Finance**. Tiền quay lại HELD; khách được báo và có thể "
                    + "mở tranh chấp.")
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<RefundResponse>> reject(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody RejectRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Refund rejected",
                refundService.reject(id, request.getNote(), userDetails.getUsername())));
    }
}
