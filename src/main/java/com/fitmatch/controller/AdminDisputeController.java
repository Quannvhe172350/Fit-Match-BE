package com.fitmatch.controller;

import com.fitmatch.common.enums.DisputeStatus;
import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.dispute.DisputeEvidenceResponse;
import com.fitmatch.dto.dispute.DisputeResponse;
import com.fitmatch.dto.dispute.ResolveDisputeRequest;
import com.fitmatch.dto.payment.DecisionNoteRequest;
import com.fitmatch.service.DisputeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/disputes")
@RequiredArgsConstructor
@Tag(name = "I. Admin - Dispute Moderation", description = "Xử lý tranh chấp (UC-065..068). Yêu cầu ROLE_ADMIN hoặc ROLE_MODERATOR.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
public class AdminDisputeController {

    private final DisputeService disputeService;

    @Operation(
            summary = "UC-065 — Hàng đợi tranh chấp",
            description = "Actor: **Moderator/Admin**. Lọc theo trạng thái (mặc định tất cả).")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<DisputeResponse>>> queue(
            @Parameter(description = "Trạng thái (bỏ trống = tất cả)")
            @RequestParam(required = false) DisputeStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(disputeService.queue(status, pageable)));
    }

    @Operation(summary = "UC-065 — Chi tiết tranh chấp (Moderator)")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DisputeResponse>> detail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(disputeService.detailForModerator(id)));
    }

    @Operation(summary = "UC-065 — Bằng chứng của tranh chấp (Moderator)")
    @GetMapping("/{id}/evidence")
    public ResponseEntity<ApiResponse<List<DisputeEvidenceResponse>>> evidence(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(disputeService.evidenceForModerator(id)));
    }

    @Operation(
            summary = "UC-065 — Bắt đầu xem xét",
            description = "Actor: **Moderator/Admin**. OPEN/ESCALATED -> UNDER_REVIEW.")
    @PostMapping("/{id}/review")
    public ResponseEntity<ApiResponse<DisputeResponse>> review(
            @PathVariable Long id, @AuthenticationPrincipal UserDetails actor) {
        return ResponseEntity.ok(ApiResponse.success("Under review",
                disputeService.startReview(actor.getUsername(), id)));
    }

    @Operation(
            summary = "UC-066/067 — Quyết định và áp dụng tài chính",
            description = "Actor: **Moderator/Admin**. resolution: REFUND_FULL/REFUND_PARTIAL/SPLIT/RELEASE_TO_GYM/NO_ACTION/PENALTY; refundAmount bắt buộc cho PARTIAL/SPLIT. Bút toán ví áp ngay trên phần held đang bảo vệ. Lỗi: 400 refundAmount không hợp lệ; 409 sai trạng thái.")
    @PostMapping("/{id}/resolve")
    public ResponseEntity<ApiResponse<DisputeResponse>> resolve(
            @PathVariable Long id,
            @Valid @RequestBody ResolveDisputeRequest request,
            @AuthenticationPrincipal UserDetails actor) {
        return ResponseEntity.ok(ApiResponse.success("Dispute resolved",
                disputeService.resolve(actor.getUsername(), id, request)));
    }

    @Operation(
            summary = "UC-068 — Đóng tranh chấp",
            description = "Actor: **Moderator/Admin**. RESOLVED -> CLOSED.")
    @PostMapping("/{id}/close")
    public ResponseEntity<ApiResponse<DisputeResponse>> close(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) DecisionNoteRequest request,
            @AuthenticationPrincipal UserDetails actor) {
        return ResponseEntity.ok(ApiResponse.success("Dispute closed",
                disputeService.close(actor.getUsername(), id, request != null ? request.getNote() : null)));
    }

    @Operation(
            summary = "UC-068 — Chuyển cấp",
            description = "Actor: **Moderator/Admin**. -> ESCALATED (trừ khi đã CLOSED).")
    @PostMapping("/{id}/escalate")
    public ResponseEntity<ApiResponse<DisputeResponse>> escalate(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) DecisionNoteRequest request,
            @AuthenticationPrincipal UserDetails actor) {
        return ResponseEntity.ok(ApiResponse.success("Dispute escalated",
                disputeService.escalate(actor.getUsername(), id, request != null ? request.getNote() : null)));
    }
}
