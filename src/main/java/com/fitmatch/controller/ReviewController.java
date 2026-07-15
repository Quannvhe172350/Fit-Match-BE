package com.fitmatch.controller;

import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.review.ReplyRequest;
import com.fitmatch.dto.review.ReportRequest;
import com.fitmatch.dto.review.ReviewRequest;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
@Tag(name = "H. Reviews", description = "Đánh giá sau buổi tập (UC-069/070). Đọc theo gym/PT là công khai; tạo/sửa/xoá cần ROLE_CUSTOMER.")
public class ReviewController {

    private final ReviewService reviewService;

    @Operation(
            summary = "UC-069 — Đánh giá của tôi",
            description = "Actor: **Customer**.")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<PageResponse<ReviewResponse>>> mine(
            @AuthenticationPrincipal UserDetails userDetails,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                reviewService.myReviews(userDetails.getUsername(), pageable)));
    }

    @Operation(
            summary = "UC-069 — Gửi đánh giá cho booking đã hoàn tất",
            description = "Actor: **Customer**. Chỉ booking COMPLETED của bạn, một lần/booking; ngữ cảnh gym/dịch vụ/PT lấy từ booking. Lỗi: 409 chưa COMPLETED/đã đánh giá; 404 không thuộc về bạn.")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping
    public ResponseEntity<ApiResponse<ReviewResponse>> create(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ReviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Review submitted", reviewService.create(userDetails.getUsername(), request)));
    }

    @Operation(
            summary = "UC-069 — Sửa đánh giá của mình",
            description = "Actor: **Customer**. Lỗi: 409 review đã bị gỡ; 404 không thuộc về bạn.")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('CUSTOMER')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ReviewResponse>> update(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody ReviewRequest request) {
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
            description = "Actor: **Authenticated user** (Customer/Gym/PT). Gửi cho moderation (UC-071). Lỗi: 409 đã có báo cáo đang xử lý.")
    @SecurityRequirement(name = "bearerAuth")
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
            summary = "UC-069 — Gym phản hồi một đánh giá",
            description = "Actor: **Gym Operator**. Phản hồi review của gym mình. Lỗi: 404 review không thuộc gym của bạn.")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('GYM_OPERATOR')")
    @PutMapping("/{id}/reply")
    public ResponseEntity<ApiResponse<ReviewResponse>> reply(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody ReplyRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Reply saved",
                reviewService.reply(userDetails.getUsername(), id, request)));
    }

    @Operation(
            summary = "UC-009 — Đánh giá công khai của Gym",
            description = "Actor: **Guest/Customer**. Chỉ review đang hiển thị (VISIBLE).")
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
}
