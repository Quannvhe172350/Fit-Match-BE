package com.fitmatch.controller;

import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.review.RatingSummaryResponse;
import com.fitmatch.dto.review.ReportRequest;
import com.fitmatch.dto.review.TicketReviewRequest;
import com.fitmatch.dto.review.ReviewResponse;
import com.fitmatch.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
@Tag(name = "H. Reviews", description = "Đánh giá sau buổi tập (UC-069/070). Chỉ khách có booking COMPLETED tại gym/với PT đó mới đánh giá được; đọc theo gym/PT là công khai. Review một chiều — không có phản hồi của gym.")
public class ReviewController {

    private final ReviewService reviewService;

    @Operation(
            summary = "UC-069 — Đánh giá của tôi",
            description = "Actor: **Customer**. targetType=GYM lấy đánh giá phòng gym, targetType=PT "
                    + "lấy đánh giá huấn luyện viên; bỏ trống = cả hai (đối chiếu 'vé/buổi nào đã "
                    + "đánh giá' cần bản không lọc).")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<PageResponse<ReviewResponse>>> mine(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) com.fitmatch.common.enums.ReviewTargetType targetType,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                reviewService.myReviews(userDetails.getUsername(), targetType, pageable)));
    }

    // Gửi đánh giá: POST /api/tickets/{id}/review (phòng gym, mở khi dùng hết vé)
    // và POST /api/sessions/{id}/review (PT, mở khi buổi xong) — câu 17 + 36.

    @Operation(
            summary = "UC-069 — Sửa đánh giá của mình",
            description = "Actor: **Customer**. Lỗi: 409 review đã bị gỡ; 404 không thuộc về bạn.")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('CUSTOMER')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ReviewResponse>> update(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody TicketReviewRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Review updated",
                reviewService.update(userDetails.getUsername(), id, request)));
    }

    @Operation(
            summary = "UC-069 — Xoá đánh giá của mình",
            description = "Actor: **Customer**. Lỗi: 404 không thuộc về bạn.")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('CUSTOMER')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        reviewService.delete(userDetails.getUsername(), id);
        return ResponseEntity.ok(ApiResponse.success("Review deleted", null));
    }

    @Operation(
            summary = "UC-070 — Báo cáo một đánh giá có vấn đề",
            description = "Actor: **Customer/Gym/PT**. Gửi cho moderation (UC-071). Lỗi: 409 đã có báo cáo đang xử lý.")
    @SecurityRequirement(name = "bearerAuth")
    // P1-1.1: UC-070 giới hạn actor Customer/Gym/PT — Admin/Moderator dùng công cụ
    // kiểm duyệt riêng, không đi qua kênh report của người dùng.
    @PreAuthorize("hasAnyRole('CUSTOMER','GYM_OPERATOR','PT')")
    @PostMapping("/{id}/report")
    public ResponseEntity<ApiResponse<Void>> report(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody ReportRequest request) {
        reviewService.report(userDetails.getUsername(), id, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Report submitted", null));
    }

    @Operation(
            summary = "UC-009 — Đánh giá công khai của Gym",
            description = "Actor: **Guest/Customer**. Chỉ review đang hiển thị (VISIBLE); review một chiều, không có phản hồi.")
    @SecurityRequirements
    @GetMapping("/gym/{gymId}")
    public ResponseEntity<ApiResponse<PageResponse<ReviewResponse>>> gymReviews(
            @PathVariable Long gymId, @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                reviewService.visibleForGym(gymId, pageable)));
    }

    @Operation(
            summary = "UC-009 — Đánh giá công khai của PT",
            description = "Actor: **Guest/Customer**. Chỉ review đang hiển thị (VISIBLE).")
    @SecurityRequirements
    @GetMapping("/pt/{ptId}")
    public ResponseEntity<ApiResponse<PageResponse<ReviewResponse>>> ptReviews(
            @PathVariable Long ptId, @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                reviewService.visibleForPt(ptId, pageable)));
    }

    @Operation(
            summary = "UC-009 — Điểm trung bình & phổ điểm của Gym",
            description = "Actor: **Guest/Customer**. Trả về averageRating, totalReviews và số lượng theo từng mức sao. "
                    + "Chỉ tính review VISIBLE — review bị gỡ/ẩn không kéo điểm xuống.")
    @SecurityRequirements
    @GetMapping("/gym/{gymId}/rating")
    public ResponseEntity<ApiResponse<RatingSummaryResponse>> gymRating(@PathVariable Long gymId) {
        return ResponseEntity.ok(ApiResponse.success(reviewService.gymRatingSummary(gymId)));
    }

    @Operation(
            summary = "UC-009 — Điểm trung bình & phổ điểm của PT",
            description = "Actor: **Guest/Customer**. Chỉ tính review VISIBLE.")
    @SecurityRequirements
    @GetMapping("/pt/{ptId}/rating")
    public ResponseEntity<ApiResponse<RatingSummaryResponse>> ptRating(@PathVariable Long ptId) {
        return ResponseEntity.ok(ApiResponse.success(reviewService.ptRatingSummary(ptId)));
    }
}
