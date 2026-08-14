package com.fitmatch.dto.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Đánh giá trong mô hình vé. Không có {@code bookingId} vì đối tượng được đánh
 * giá đã nằm trong đường dẫn: {@code /tickets/{id}/review} cho phòng gym và
 * {@code /sessions/{id}/review} cho PT.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketReviewRequest {

    @NotNull(message = "rating is required")
    @Min(value = 1, message = "rating must be 1..5")
    @Max(value = 5, message = "rating must be 1..5")
    private Integer rating;

    @Size(max = 2000)
    private String comment;

    /** Ảnh đính kèm — id từ POST /api/media/upload (entityType=REVIEW). */
    @Size(max = 10, message = "at most 10 images per review")
    private List<Long> mediaIds;
}
