package com.fitmatch.dto.gym;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Thêm ảnh/media cho Gym hoặc chi nhánh (UC-016). URL lấy từ /api/files/upload.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GymMediaRequest {

    @NotBlank(message = "Media URL is required")
    @Size(max = 500)
    private String url;

    @Size(max = 255)
    private String caption;

    /** Ảnh của chi nhánh cụ thể (phải thuộc Gym); null = ảnh chung. */
    private Long branchId;
}
