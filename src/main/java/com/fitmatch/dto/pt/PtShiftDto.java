package com.fitmatch.dto.pt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Một ca PT được xếp trong một ngày — màn "Lịch ca của tôi", READ-ONLY.
 * PT không sửa được gì ở đây; muốn nghỉ thì gửi đơn (quyết định §4.2).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PtShiftDto {

    private LocalDate date;
    private Long shiftId;
    private String shiftName;
    private Long branchId;
    private String branchName;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer slotMinutes;

    /** Có đơn nghỉ ĐÃ DUYỆT phủ ca này — PT thấy ngay ca nào đã được nghỉ. */
    private boolean onLeave;

    /** Số buổi khách đã đặt trong ca này. */
    private int bookedSessions;
}
