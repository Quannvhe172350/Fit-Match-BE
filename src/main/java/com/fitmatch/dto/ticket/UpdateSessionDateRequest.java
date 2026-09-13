package com.fitmatch.dto.ticket;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** Dời ngày tập — chỉ vé DAY; vé nhiều ngày chỉ đổi giờ trong ngày (sheet 7). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSessionDateRequest {

    @NotNull(message = "date is required")
    private LocalDate date;
}
