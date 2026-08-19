package com.fitmatch.dto.gym;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Quyết định §4.2: hạn mức đơn nghỉ mỗi tháng cho một PT, do Gym tự đặt.
 * {@code enabled=false} (mặc định) = không giới hạn — bật tính năng là một
 * lựa chọn có ý thức, không phải trạng thái mặc nhiên.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GymLeavePolicyDto {

    @NotNull(message = "enabled is required")
    private Boolean enabled;

    /** Bắt buộc khi enabled=true. Đếm theo tháng của fromDate; chỉ đơn PENDING/APPROVED tiêu lượt. */
    @Min(value = 1, message = "monthlyQuota must be at least 1")
    @Max(value = 31, message = "monthlyQuota must not exceed 31")
    private Integer monthlyQuota;
}
