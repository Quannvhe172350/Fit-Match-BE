package com.fitmatch.dto.ticket;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Đặt lịch cho một vé.
 *
 * <ul>
 *   <li>Vé DAY: gửi {@code date} (+ {@code ptId}/{@code slotStart} nếu vé có PT).</li>
 *   <li>Vé PACKAGE: gửi {@code startDate}; server tự sinh dayCount ngày liên
 *       tiếp (câu 27). {@code days} là tuỳ chọn, chỉ để gán PT cho từng ngày —
 *       ngày nào không có trong danh sách thì để trống PT, bổ sung sau (câu 8).</li>
 * </ul>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleTicketRequest {

    /** Vé DAY: ngày tập. */
    private LocalDate date;

    /** Vé DAY có PT: PT và giờ bắt đầu. */
    private Long ptId;
    private LocalTime slotStart;

    /** Vé PACKAGE: ngày bắt đầu, các ngày còn lại là ngày liên tiếp sau đó. */
    private LocalDate startDate;

    /** Vé PACKAGE có PT: gán PT cho từng ngày. Bỏ trống ngày nào cũng được. */
    @Valid
    private List<DayPt> days;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DayPt {
        /** 1..dayCount. */
        private Integer dayIndex;
        private Long ptId;
        private LocalTime slotStart;
    }
}
