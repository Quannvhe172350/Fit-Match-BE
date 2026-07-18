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

    /** Điểm đánh giá trung bình (VISIBLE) và số lượt — UC-071. */
    private java.math.BigDecimal averageRating;
    private Long reviewCount;

    /** Phòng gym quản lý PT — để khách điều hướng sang trang gym trước khi đặt PT. */
    private Long gymId;
    private String gymName;

    /**
     * Bug 14 (UC-019/021): badge "Xác thực" phải data-driven — PT được coi là
     * xác thực khi đang ACTIVE và thuộc gym đã được duyệt (mô hình gym chịu
     * trách nhiệm; platform verification PT đã gỡ — 410).
     */
    private boolean verified;

    public static PtPublicProfileResponse of(PtProfile p, List<CertificationResponse> certifications) {
        var gym = p.getGymProfile();
        boolean gymApproved = gym != null
                && gym.getVerificationStatus() == com.fitmatch.common.enums.VerificationStatus.APPROVED;
        return PtPublicProfileResponse.builder()
                .id(p.getId())
                .displayName(p.getDisplayName())
                .bio(p.getBio())
                .serviceArea(p.getServiceArea())
                .specialization(p.getSpecialization())
                .experienceYears(p.getExperienceYears())
                .certifications(certifications)
                .gymId(gym != null ? gym.getId() : null)
                .gymName(gym != null ? gym.getGymName() : null)
                .verified(p.getStatus() == com.fitmatch.common.enums.PtStatus.ACTIVE && gymApproved)
                .build();
    }

    public static PtPublicProfileResponse of(PtProfile p, List<CertificationResponse> certifications,
                                             java.math.BigDecimal avg, long count) {
        PtPublicProfileResponse r = of(p, certifications);
        r.setAverageRating(avg);
        r.setReviewCount(count);
        return r;
    }
}
