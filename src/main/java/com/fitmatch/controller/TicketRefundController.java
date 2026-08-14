package com.fitmatch.controller;

import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.dispute.DisputeResponse;
import com.fitmatch.dto.payment.RefundResponse;
import com.fitmatch.dto.review.ReviewResponse;
import com.fitmatch.dto.review.TicketReviewRequest;
import com.fitmatch.service.TicketDisputeService;
import com.fitmatch.service.TicketRefundService;
import com.fitmatch.service.TicketReviewService;
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

/**
 * Ba việc khách làm SAU khi mua vé: xin hoàn tiền, mở tranh chấp, và đánh giá.
 * Gom một chỗ vì cả ba đều là hậu quả của cùng một vé.
 */
@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
@Tag(name = "C. Ticket Aftercare", description = "Hoàn tiền, tranh chấp và đánh giá của khách")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('CUSTOMER')")
public class TicketRefundController {

    private final TicketRefundService refundService;
    private final TicketDisputeService disputeService;
    private final TicketReviewService reviewService;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReasonRequest {
        @NotBlank(message = "reason is required")
        @Size(max = 1000)
        private String reason;
    }

    @Operation(summary = "Yêu cầu hoàn tiền cho một vé",
            description = "Actor: **Customer** (chủ vé). MỌI yêu cầu đều vào hàng đợi Admin — "
                    + "không có nhánh tự duyệt. Lỗi: 409 vé đã hết hạn (câu 32), vé không ở trạng "
                    + "thái dùng được, hoặc đã có yêu cầu đang chờ.")
    @PostMapping("/{id}/refund-request")
    public ResponseEntity<ApiResponse<RefundResponse>> requestRefund(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody ReasonRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Refund requested",
                refundService.requestByCustomer(userDetails.getUsername(), id, request.getReason())));
    }

    @Operation(summary = "Yêu cầu hoàn tiền của tôi", description = "Actor: **Customer**.")
    @GetMapping("/refunds")
    public ResponseEntity<ApiResponse<PageResponse<RefundResponse>>> myRefunds(
            @AuthenticationPrincipal UserDetails userDetails,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                refundService.listForCustomer(userDetails.getUsername(), pageable)));
    }

    @Operation(summary = "Mở tranh chấp cho vé hoặc cho một buổi tập",
            description = "Actor: **Customer** (bên liên quan). Không truyền sessionId = tranh chấp "
                    + "CẤP VÉ (đóng băng toàn bộ phần đang giữ). Truyền sessionId = tranh chấp CẤP "
                    + "BUỔI, chỉ đóng băng giá trị một ngày tập (payableAmount / dayCount).")
    @PostMapping("/{id}/disputes")
    public ResponseEntity<ApiResponse<DisputeResponse>> openDispute(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @RequestParam(required = false) Long sessionId,
            @Valid @RequestBody ReasonRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Dispute opened",
                disputeService.open(userDetails.getUsername(), id, sessionId, request.getReason())));
    }

    @Operation(summary = "Đánh giá phòng gym",
            description = "Actor: **Customer** (chủ vé). Mở khi vé đã dùng hết (USED_UP) — câu 17. "
                    + "Mỗi vé đúng một đánh giá.")
    @PostMapping("/{id}/review")
    public ResponseEntity<ApiResponse<ReviewResponse>> reviewGym(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody TicketReviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Review created",
                reviewService.reviewGym(userDetails.getUsername(), id, request)));
    }
}
