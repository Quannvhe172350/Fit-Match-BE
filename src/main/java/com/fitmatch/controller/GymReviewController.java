package com.fitmatch.controller;

import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.review.ReviewResponse;
import com.fitmatch.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/gym/reviews")
@RequiredArgsConstructor
@Tag(name = "H. Gym - Reviews", description = "Gym xem toàn bộ đánh giá của mình để theo dõi chất lượng (UC-023/069). Chỉ đọc — review là một chiều. Yêu cầu ROLE_GYM_OPERATOR.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('GYM_OPERATOR')")
public class GymReviewController {

    private final ReviewService reviewService;

    @Operation(
            summary = "UC-023/069 — Đánh giá của gym tôi (mọi trạng thái)",
            description = "Actor: **Gym Operator**. Gồm cả review bị ẩn/gỡ để theo dõi chất lượng PT/dịch vụ; "
                    + "nội dung không phản hồi được, chỉ báo cáo vi phạm qua POST /api/reviews/{id}/report. "
                    + "targetType=GYM lấy đánh giá phòng gym, targetType=PT lấy đánh giá huấn luyện viên; "
                    + "bỏ trống = cả hai.")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ReviewResponse>>> myGymReviews(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) com.fitmatch.common.enums.ReviewTargetType targetType,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                reviewService.gymReviews(userDetails.getUsername(), targetType, pageable)));
    }
}
