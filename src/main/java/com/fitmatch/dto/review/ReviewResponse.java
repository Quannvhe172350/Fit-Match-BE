package com.fitmatch.dto.review;

import com.fitmatch.common.enums.ReviewStatus;
import com.fitmatch.entity.Review;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/** Đánh giá (UC-069). Giữ tên trường tương thích client cũ (customerName, ptName...). */
@Getter
@Builder
public class ReviewResponse {

    private Long id;
    private Long bookingId;
    private String customerName;
    private Long gymId;
    private String gymName;
    private Long serviceId;
    private String serviceName;
    private Long ptProfileId;
    private String ptName;
    private int rating;
    private String comment;
    private ReviewStatus status;
    private String reply;
    private String repliedByName;
    private LocalDateTime repliedAt;
    private LocalDateTime createdAt;

    public static ReviewResponse of(Review r) {
        return ReviewResponse.builder()
                .id(r.getId())
                .bookingId(r.getBooking().getId())
                .customerName(r.getCustomer().getUsername())
                .gymId(r.getGymProfile().getId())
                .gymName(r.getGymProfile().getGymName())
                .serviceId(r.getGymService() != null ? r.getGymService().getId() : null)
                .serviceName(r.getGymService() != null ? r.getGymService().getName() : null)
                .ptProfileId(r.getPtProfile() != null ? r.getPtProfile().getId() : null)
                .ptName(r.getPtProfile() != null ? r.getPtProfile().getDisplayName() : null)
                .rating(r.getRating())
                .comment(r.getComment())
                .status(r.getStatus())
                .reply(r.getReply())
                .repliedByName(r.getRepliedBy())
                .repliedAt(r.getRepliedAt())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
