package com.fitmatch.dto.booking;

import com.fitmatch.common.enums.BookingStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Hiệu chỉnh bản ghi điểm danh/hoàn tất (UC-050) — kèm lý do, ghi audit.
 * checkedInAt: sửa mốc check-in (clearCheckIn=true để xoá).
 * status: chỉ cho phép đổi COMPLETED <-> NO_SHOW khi tiền chưa giải ngân.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CorrectAttendanceRequest {

    private LocalDateTime checkedInAt;

    private Boolean clearCheckIn;

    private BookingStatus status;

    @NotBlank(message = "Reason is required")
    @Size(max = 500)
    private String reason;
}
