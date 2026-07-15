package com.fitmatch.dto.review;

import com.fitmatch.common.enums.ReviewStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Moderator quyết định trạng thái review (UC-071): VISIBLE/HIDDEN/REMOVED. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ModerateReviewRequest {

    @NotNull(message = "status is required")
    private ReviewStatus status;

    @Size(max = 500)
    private String note;
}
