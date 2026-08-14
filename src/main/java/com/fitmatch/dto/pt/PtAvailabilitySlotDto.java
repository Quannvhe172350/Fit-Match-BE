package com.fitmatch.dto.pt;

import com.fitmatch.entity.PtAvailability;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

/** Một khung giờ rảnh của PT trong một NGÀY cụ thể (thay mô hình lặp theo thứ). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PtAvailabilitySlotDto {

    @NotNull(message = "date is required")
    private LocalDate date;

    @NotNull(message = "startTime is required")
    private LocalTime startTime;

    @NotNull(message = "endTime is required")
    private LocalTime endTime;

    public static PtAvailabilitySlotDto of(PtAvailability a) {
        return PtAvailabilitySlotDto.builder()
                .date(a.getSlotDate())
                .startTime(a.getStartTime())
                .endTime(a.getEndTime())
                .build();
    }
}
