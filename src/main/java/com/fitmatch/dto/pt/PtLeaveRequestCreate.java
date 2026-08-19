package com.fitmatch.dto.pt;

import com.fitmatch.common.enums.LeaveScope;
import com.fitmatch.common.enums.LeaveType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * PT gửi đơn xin nghỉ / báo bận. Ràng buộc phụ thuộc scope (SHIFT phải có
 * shiftIds, TIME_RANGE phải có start/end) kiểm ở service — Bean Validation
 * không diễn đạt được "bắt buộc khi trường khác bằng X" mà không dựng thêm một
 * annotation riêng cho đúng một chỗ dùng.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PtLeaveRequestCreate {

    @NotNull(message = "type is required")
    private LeaveType type;

    @NotNull(message = "scope is required")
    private LeaveScope scope;

    @NotNull(message = "fromDate is required")
    private LocalDate fromDate;

    @NotNull(message = "toDate is required")
    private LocalDate toDate;

    /** Chỉ dùng khi scope = SHIFT. Nghỉ ca sáng nhưng vẫn dạy ca chiều là hợp lệ. */
    private List<Long> shiftIds;

    /** Chỉ dùng khi scope = TIME_RANGE. */
    private LocalTime startTime;

    private LocalTime endTime;

    @NotBlank(message = "reason is required")
    @Size(min = 10, max = 1000, message = "reason must be 10..1000 characters")
    private String reason;

    /** URL trả về từ POST /api/files/upload. Tuỳ chọn — giấy nghỉ ốm, đơn thuốc... */
    @Size(max = 500, message = "attachmentUrl must not exceed 500 characters")
    private String attachmentUrl;
}
