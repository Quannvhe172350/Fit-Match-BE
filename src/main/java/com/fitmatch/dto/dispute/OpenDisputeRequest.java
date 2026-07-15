package com.fitmatch.dto.dispute;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Mở tranh chấp cho một booking (UC-063). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OpenDisputeRequest {

    @NotNull(message = "bookingId is required")
    private Long bookingId;

    @NotBlank(message = "reason is required")
    @Size(max = 1000)
    private String reason;
}
