package com.fitmatch.dto.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

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

    /**
     * Ảnh đính kèm — id trả về từ {@code POST /api/media/upload} với
     * {@code entityType=REVIEW, imageType=REVIEW_IMAGE} và không truyền entityId.
     * Chỉ gắn được ảnh do chính người gửi đánh giá upload; ảnh của người khác bị
     * từ chối 404. Khi sửa đánh giá, danh sách này là trạng thái CUỐI CÙNG: ảnh cũ
     * không còn trong danh sách sẽ bị gỡ và xoá khỏi storage.
     */
    @Size(max = 10, message = "at most 10 images per review")
    private List<Long> mediaIds;
}
