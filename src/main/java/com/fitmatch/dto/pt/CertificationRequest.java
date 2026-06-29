package com.fitmatch.dto.pt;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Dùng cho cả tạo & cập nhật chứng chỉ (UC-27).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CertificationRequest {

    @NotBlank(message = "Certification name is required")
    @Size(max = 200)
    private String name;

    @Size(max = 200)
    private String issuingOrganization;

    private LocalDate issueDate;

    private LocalDate expiryDate;

    @Size(max = 500)
    private String credentialUrl;
}
