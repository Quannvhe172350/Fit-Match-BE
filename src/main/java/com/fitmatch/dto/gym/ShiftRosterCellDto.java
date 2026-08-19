package com.fitmatch.dto.gym;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Một ô của lưới phân ca (hàng = PT, cột = ngày, ô = các ca). Phẳng chứ không
 * lồng: FE tự gom theo (ptProfileId, date), và cùng một cấu trúc dùng lại được
 * cho cả xem tuần lẫn xem tháng.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShiftRosterCellDto {

    private Long assignmentId;
    private Long ptProfileId;
    private String ptName;
    private LocalDate date;

    private Long shiftId;
    private String shiftName;
    private LocalTime startTime;
    private LocalTime endTime;

    /** RECURRING = sinh từ xếp lặp; MANUAL = Gym thêm tay. */
    private String source;

    private boolean active;

    /** PT có đơn nghỉ ĐÃ DUYỆT phủ ca này — FE tô khác màu, không hiện như ca thường. */
    private boolean onLeave;

    /** Số buổi khách đã đặt trong ca này — Gym phải thấy trước khi định gỡ ca. */
    private int bookedSessions;
}
