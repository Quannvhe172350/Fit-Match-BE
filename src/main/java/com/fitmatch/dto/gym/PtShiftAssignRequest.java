package com.fitmatch.dto.gym;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

/**
 * Xếp PT vào một ca. Một request phục vụ cả hai kiểu thao tác:
 *
 * <ul>
 *   <li>xếp LẶP — {@code from != to} và {@code daysOfWeek} có giá trị
 *       ("ca tối, T2/T4/T6, 01/09 - 30/09");</li>
 *   <li>xếp LẺ — {@code from == to}, bỏ trống {@code daysOfWeek}.</li>
 * </ul>
 *
 * Gộp làm một vì phần khó (kiểm chồng ca, kiểm giờ mở cửa, upsert idempotent)
 * giống hệt nhau; tách hai endpoint chỉ nhân đôi chỗ dễ sai.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PtShiftAssignRequest {

    @NotNull(message = "shiftId is required")
    private Long shiftId;

    @NotNull(message = "from is required")
    private LocalDate from;

    @NotNull(message = "to is required")
    private LocalDate to;

    /** Bỏ trống = mọi thứ trong tuần mà ca đó áp dụng. */
    private List<@Min(value = 1, message = "daysOfWeek must be 1 (Mon) .. 7 (Sun)")
            @Max(value = 7, message = "daysOfWeek must be 1 (Mon) .. 7 (Sun)") Integer> daysOfWeek;
}
