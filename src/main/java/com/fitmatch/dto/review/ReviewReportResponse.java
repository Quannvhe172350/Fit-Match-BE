package com.fitmatch.dto.review;

import com.fitmatch.common.enums.ReportStatus;
import com.fitmatch.entity.ReviewReport;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/** Báo cáo review kèm nội dung review liên quan (UC-070/071). */
@Getter
@Builder
public class ReviewReportResponse {

    private Long id;
    private ReportStatus status;
    private String reason;
    private String reportedBy;
    private String moderatorNote;
    private LocalDateTime createdAt;
    private ReviewResponse review;

    public static ReviewReportResponse of(ReviewReport rr) {
        return ReviewReportResponse.builder()
                .id(rr.getId())
                .status(rr.getStatus())
                .reason(rr.getReason())
                .reportedBy(rr.getCreatedBy())
                .moderatorNote(rr.getModeratorNote())
                .createdAt(rr.getCreatedAt())
                .review(ReviewResponse.of(rr.getReview()))
                .build();
    }
}
