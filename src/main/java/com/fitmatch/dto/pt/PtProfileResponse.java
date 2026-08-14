package com.fitmatch.dto.pt;

import com.fitmatch.common.enums.VerificationStatus;
import com.fitmatch.entity.PtProfile;
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
public class PtProfileResponse {

    private Long id;
    private String username;
    private String displayName;
    private String bio;
    private String serviceArea;
    private String specialization;
    private Integer experienceYears;
    /** Ảnh đại diện hồ sơ PT (media TRAINER/AVATAR) — xem PtPublicProfileResponse. */
    private String avatarUrl;
    private VerificationStatus verificationStatus;
    private String rejectionReason;
    private boolean active;
    /** Bug 14: trạng thái vận hành thật (UC-019/021) — nguồn sự thật cho admin, thay vì verificationStatus (deprecated). */
    private com.fitmatch.common.enums.PtStatus status;
    private List<PtDocumentDto> documents;

    public static PtProfileResponse of(PtProfile p, List<PtDocumentDto> documents) {
        return PtProfileResponse.builder()
                .id(p.getId())
                .username(p.getUser() != null ? p.getUser().getUsername() : null)
                .displayName(p.getDisplayName())
                .bio(p.getBio())
                .serviceArea(p.getServiceArea())
                .specialization(p.getSpecialization())
                .experienceYears(p.getExperienceYears())
                .verificationStatus(p.getVerificationStatus())
                .rejectionReason(p.getRejectionReason())
                .active(p.isActive())
                .status(p.getStatus())
                .documents(documents)
                .build();
    }
}
