package com.fitmatch.dto.gym;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Tạo/cập nhật cơ sở vật chất (UC-47/48).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FacilityRequest {

    @NotBlank(message = "Facility name is required")
    @Size(max = 150)
    private String name;

    @Size(max = 1000)
    private String description;

    /** UC-016: chi nhánh chứa cơ sở vật chất (phải thuộc Gym); null = chưa gắn. */
    private Long branchId;

    /**
     * Ảnh minh hoạ cơ sở vật chất (media FACILITY/GALLERY đã upload trước).
     *
     * <p>Đây là TRẠNG THÁI CUỐI CÙNG chứ không phải "thêm vào": ảnh đang gắn mà
     * vắng mặt trong danh sách sẽ bị xoá hẳn. {@code null} = không đụng tới ảnh,
     * nên client cũ chỉ sửa tên/mô tả vẫn chạy đúng.
     */
    private java.util.List<Long> mediaIds;
}
