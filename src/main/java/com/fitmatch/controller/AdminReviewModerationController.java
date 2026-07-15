package com.fitmatch.controller;

import com.fitmatch.common.enums.ReportStatus;
import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.payment.DecisionNoteRequest;
import com.fitmatch.dto.review.ModerateReviewRequest;
import com.fitmatch.dto.review.ReviewReportResponse;
import com.fitmatch.dto.review.ReviewResponse;
import com.fitmatch.service.ReviewService;
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

@RestController
@RequestMapping("/api/admin/reviews")
@RequiredArgsConstructor
@Tag(name = "H. Admin - Review Moderation", description = "Kiểm duyệt đánh giá & báo cáo (UC-071). Yêu cầu ROLE_ADMIN hoặc ROLE_MODERATOR.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
public class AdminReviewModerationController {

    private final ReviewService reviewService;

    @Operation(
            summary = "UC-071 — Danh sách báo cáo review",
            description = "Actor: **Moderator/Admin**. Lọc theo trạng thái (mặc định OPEN); kèm nội dung review liên quan.")
    @GetMapping("/reports")
    public ResponseEntity<ApiResponse<PageResponse<ReviewReportResponse>>> reports(
            @Parameter(description = "Trạng thái (mặc định OPEN)")
            @RequestParam(required = false) ReportStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(reviewService.reports(status, pageable)));
    }

    @Operation(
            summary = "UC-071 — Kiểm duyệt trạng thái review",
            description = "Actor: **Moderator/Admin**. Đặt VISIBLE/HIDDEN/REMOVED kèm ghi chú (ghi audit); chỉ review VISIBLE mới tính vào điểm trung bình.")
    @PostMapping("/{id}/moderate")
    public ResponseEntity<ApiResponse<ReviewResponse>> moderate(
            @PathVariable Long id,
            @Valid @RequestBody ModerateReviewRequest request,
            @AuthenticationPrincipal UserDetails actor) {
        return ResponseEntity.ok(ApiResponse.success("Review moderated",
                reviewService.moderate(actor.getUsername(), id, request)));
    }

    @Operation(
            summary = "UC-071 — Đóng báo cáo (đã xử lý)",
            description = "Actor: **Moderator/Admin**. Đánh dấu RESOLVED sau khi đã kiểm duyệt review liên quan.")
    @PostMapping("/reports/{id}/resolve")
    public ResponseEntity<ApiResponse<ReviewReportResponse>> resolve(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) DecisionNoteRequest request,
            @AuthenticationPrincipal UserDetails actor) {
        return ResponseEntity.ok(ApiResponse.success("Report resolved",
                reviewService.resolveReport(actor.getUsername(), id, false,
                        request != null ? request.getNote() : null)));
    }

    @Operation(
            summary = "UC-071 — Bỏ qua báo cáo (không hợp lệ)",
            description = "Actor: **Moderator/Admin**. Đánh dấu DISMISSED.")
    @PostMapping("/reports/{id}/dismiss")
    public ResponseEntity<ApiResponse<ReviewReportResponse>> dismiss(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) DecisionNoteRequest request,
            @AuthenticationPrincipal UserDetails actor) {
        return ResponseEntity.ok(ApiResponse.success("Report dismissed",
                reviewService.resolveReport(actor.getUsername(), id, true,
                        request != null ? request.getNote() : null)));
    }
}
