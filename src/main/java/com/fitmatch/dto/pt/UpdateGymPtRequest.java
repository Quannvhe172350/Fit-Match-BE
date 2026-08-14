package com.fitmatch.dto.pt;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Gym cập nhật một phần hồ sơ PT thuộc quyền quản lý (UC-019). Field null = giữ nguyên.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateGymPtRequest {

    @Size(max = 120)
    private String displayName;

    @Size(max = 2000)
    private String bio;

    @Size(max = 255)
    private String specialization;

    @Size(max = 255)
    private String serviceArea;

    private Integer experienceYears;

    /** Liên hệ của PT (nằm ở User). Chuỗi rỗng = xoá số hiện có. */
    @Size(max = 30)
    @com.fitmatch.common.validation.VietnamPhone
    private String phone;
}
