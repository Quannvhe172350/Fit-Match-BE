package com.fitmatch.dto.pt;

import com.fitmatch.entity.PtProfile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Góc nhìn công khai của hồ sơ PT (UC-28 preview, UC-14 marketplace).
 * KHÔNG bao gồm tài liệu xác minh nội bộ hay lý do từ chối.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PtPublicProfileResponse {

    private Long id;
    private String displayName;
    private String bio;
    private String serviceArea;
    private String specialization;
    private Integer experienceYears;
    private List<CertificationResponse> certifications;

    public static PtPublicProfileResponse of(PtProfile p, List<CertificationResponse> certifications) {
        return PtPublicProfileResponse.builder()
                .id(p.getId())
                .displayName(p.getDisplayName())
                .bio(p.getBio())
                .serviceArea(p.getServiceArea())
                .specialization(p.getSpecialization())
                .experienceYears(p.getExperienceYears())
                .certifications(certifications)
                .build();
    }
}
