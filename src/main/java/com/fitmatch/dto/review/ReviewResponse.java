package com.fitmatch.dto.review;

import com.fitmatch.common.enums.ReviewStatus;
import com.fitmatch.dto.media.MediaResponse;
import com.fitmatch.entity.Review;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Đánh giá (UC-069). Giữ tên trường tương thích client cũ (customerName, ptName...).
 * V59: bỏ reply/repliedBy/repliedAt — review chỉ hiển thị, gym không phản hồi.
 * V64: thêm {@code images} — ảnh khách đính kèm, lưu trên GCS qua Media system.
 */
@Getter
@Builder
public class ReviewResponse {

    private Long id;
    /** Mô hình vé: GYM neo vào ticketId, PT neo vào sessionId. */
    private Long ticketId;
    private Long sessionId;
    private com.fitmatch.common.enums.ReviewTargetType targetType;
    private String customerName;
    private Long gymId;
    private String gymName;
    private Long ptProfileId;
    private String ptName;
    private int rating;
    private String comment;
    private ReviewStatus status;
    private List<MediaResponse> images;
    private LocalDateTime createdAt;

    public static ReviewResponse of(Review r) {
        return of(r, List.of());
    }

    public static ReviewResponse of(Review r, List<MediaResponse> images) {
        return ReviewResponse.builder()
                .id(r.getId())
                .ticketId(r.getTicket() != null ? r.getTicket().getId() : null)
                .sessionId(r.getSession() != null ? r.getSession().getId() : null)
                .targetType(r.getTargetType())
                .customerName(r.getCustomer().getUsername())
                .gymId(r.getGymProfile().getId())
                .gymName(r.getGymProfile().getGymName())
                .ptProfileId(r.getPtProfile() != null ? r.getPtProfile().getId() : null)
                .ptName(r.getPtProfile() != null ? r.getPtProfile().getDisplayName() : null)
                .rating(r.getRating())
                .comment(r.getComment())
                .status(r.getStatus())
                .images(images != null ? images : List.of())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
