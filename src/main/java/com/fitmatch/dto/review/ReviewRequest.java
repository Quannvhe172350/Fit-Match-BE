package com.fitmatch.dto.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Customer gửi/sửa đánh giá cho một booking COMPLETED (UC-069). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReviewRequest {

    @NotNull(message = "bookingId is required")
    private Long bookingId;

    @NotNull(message = "rating is required")
    @Min(value = 1, message = "rating must be 1..5")
    @Max(value = 5, message = "rating must be 1..5")
    private Integer rating;

    @Size(max = 2000)
    private String comment;
}
