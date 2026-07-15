package com.fitmatch.dto.dispute;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Gửi bằng chứng cho tranh chấp (UC-064). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DisputeEvidenceRequest {

    @NotBlank(message = "description is required")
    @Size(max = 2000)
    private String description;

    /** URL file bằng chứng (upload qua /api/files). */
    @Size(max = 500)
    private String fileUrl;
}
