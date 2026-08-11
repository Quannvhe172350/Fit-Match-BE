package com.fitmatch.dto.media;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Sửa thuộc tính hiển thị của một ảnh (không đổi file). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MediaUpdateRequest {

    @Size(max = 255)
    private String caption;

    /** Đặt ảnh này làm ảnh chính; ảnh chính cũ cùng loại sẽ tự bị bỏ cờ. */
    private Boolean primary;

    private Integer sortOrder;
}
