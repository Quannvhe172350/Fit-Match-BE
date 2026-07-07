package com.fitmatch.dto.pt;

import com.fitmatch.entity.AvailabilitySlot;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

/**
 * Một khung giờ rảnh lặp hàng tuần của PT (UC-028).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvailabilitySlotDto {

    @NotNull(message = "dayOfWeek is required")
    @Min(value = 1, message = "dayOfWeek must be 1 (Mon) .. 7 (Sun)")
    @Max(value = 7, message = "dayOfWeek must be 1 (Mon) .. 7 (Sun)")
    private Integer dayOfWeek;

    @NotNull(message = "startTime is required")
    private LocalTime startTime;

    @NotNull(message = "endTime is required")
    private LocalTime endTime;

    public static AvailabilitySlotDto of(AvailabilitySlot s) {
        return AvailabilitySlotDto.builder()
                .dayOfWeek(s.getDayOfWeek())
                .startTime(s.getStartTime())
                .endTime(s.getEndTime())
                .build();
    }
}
