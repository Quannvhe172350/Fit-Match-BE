package com.fitmatch.dto.pt;

import com.fitmatch.entity.PtCertification;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CertificationResponse {

    private Long id;
    private String name;
    private String issuingOrganization;
    private LocalDate issueDate;
    private LocalDate expiryDate;
    private String credentialUrl;

    public static CertificationResponse of(PtCertification c) {
        return CertificationResponse.builder()
                .id(c.getId())
                .name(c.getName())
                .issuingOrganization(c.getIssuingOrganization())
                .issueDate(c.getIssueDate())
                .expiryDate(c.getExpiryDate())
                .credentialUrl(c.getCredentialUrl())
                .build();
    }
}
