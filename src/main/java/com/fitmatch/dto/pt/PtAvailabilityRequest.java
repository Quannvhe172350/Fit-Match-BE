package com.fitmatch.dto.pt;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

/**
 * Khai lịch rảnh cho một KHOẢNG ngày. Toàn bộ khung giờ hiện có trong
 * [from, to] bị thay bằng {@code slots} — gửi danh sách rỗng nghĩa là xoá sạch
 * khoảng đó (PT nghỉ). Khoảng phải nêu tường minh để việc lưu lịch tháng 9
 * không đụng vào tháng 10.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PtAvailabilityRequest {

    @NotNull(message = "from is required")
    private LocalDate from;

    @NotNull(message = "to is required")
    private LocalDate to;

    @Valid
    private List<PtAvailabilitySlotDto> slots;
}
