package com.fitmatch.dto.ticket;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** Dời ngày tập — cả vé DAY lẫn vé gói (V94). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSessionDateRequest {

    @NotNull(message = "date is required")
    private LocalDate date;
}
