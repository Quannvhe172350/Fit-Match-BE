package com.fitmatch.dto.pt;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Thay toàn bộ lịch rảnh tuần của PT (UC-028). Danh sách rỗng = không nhận lịch.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAvailabilityRequest {

    @NotNull(message = "slots is required")
    @Valid
    private List<AvailabilitySlotDto> slots;
}
