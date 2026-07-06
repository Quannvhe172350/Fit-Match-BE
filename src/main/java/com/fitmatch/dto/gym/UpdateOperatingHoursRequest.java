package com.fitmatch.dto.gym;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Thay toàn bộ lịch hoạt động tuần của chi nhánh (UC-017).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOperatingHoursRequest {

    @NotEmpty(message = "hours must not be empty")
    @Valid
    private List<OperatingHourDto> hours;
}
