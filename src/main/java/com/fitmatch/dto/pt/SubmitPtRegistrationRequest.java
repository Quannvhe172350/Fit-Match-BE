package com.fitmatch.dto.pt;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitPtRegistrationRequest {

    @NotBlank(message = "Display name is required")
    @Size(max = 120)
    private String displayName;

    @Size(max = 2000)
    private String bio;

    @Size(max = 255)
    private String serviceArea;

    @Size(max = 255)
    private String specialization;

    @PositiveOrZero(message = "Experience years must be >= 0")
    private Integer experienceYears;

    @NotEmpty(message = "At least one verification document is required")
    @Valid
    private List<PtDocumentDto> documents;
}
