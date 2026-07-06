package com.fitmatch.dto.gym;

import com.fitmatch.entity.OperatingHour;
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
 * Giờ hoạt động một ngày trong tuần (UC-017). closed=true thì bỏ qua open/close.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperatingHourDto {

    @NotNull(message = "dayOfWeek is required")
    @Min(value = 1, message = "dayOfWeek must be 1 (Mon) .. 7 (Sun)")
    @Max(value = 7, message = "dayOfWeek must be 1 (Mon) .. 7 (Sun)")
    private Integer dayOfWeek;

    private LocalTime openTime;

    private LocalTime closeTime;

    private Boolean closed;

    public static OperatingHourDto of(OperatingHour h) {
        return OperatingHourDto.builder()
                .dayOfWeek(h.getDayOfWeek())
                .openTime(h.getOpenTime())
                .closeTime(h.getCloseTime())
                .closed(h.isClosed())
                .build();
    }
}
