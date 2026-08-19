package com.fitmatch.dto.gym;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;
import java.util.List;

/**
 * Tạo / sửa một ca của chi nhánh. Ràng buộc nặng (nằm trong giờ mở cửa, không
 * chồng ca khác, độ dài chia hết slotMinutes) kiểm ở service vì cần đọc DB —
 * ở đây chỉ chặn những thứ nhìn vào body là biết sai.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GymShiftRequest {

    @NotBlank(message = "name is required")
    @Size(max = 100, message = "name must not exceed 100 characters")
    private String name;

    @NotNull(message = "startTime is required")
    private LocalTime startTime;

    @NotNull(message = "endTime is required")
    private LocalTime endTime;

    /** Độ dài slot khách đặt được. 15 phút là mức nhỏ nhất còn có nghĩa với một buổi PT. */
    @NotNull(message = "slotMinutes is required")
    @Min(value = 15, message = "slotMinutes must be at least 15")
    @Max(value = 240, message = "slotMinutes must not exceed 240")
    private Integer slotMinutes;

    /** ISO-8601: 1 = Thứ hai ... 7 = Chủ nhật. */
    @NotEmpty(message = "daysOfWeek is required")
    private List<@Min(value = 1, message = "daysOfWeek must be 1 (Mon) .. 7 (Sun)")
            @Max(value = 7, message = "daysOfWeek must be 1 (Mon) .. 7 (Sun)") Integer> daysOfWeek;

    private Boolean active;
}
