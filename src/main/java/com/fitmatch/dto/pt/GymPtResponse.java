package com.fitmatch.dto.pt;

import com.fitmatch.common.enums.PtStatus;
import com.fitmatch.entity.PtProfile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Hồ sơ PT dưới góc nhìn Gym quản lý (UC-019..021).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GymPtResponse {

    private Long id;
    private String username;
    private String email;
    /** Liên hệ của PT (User.phone) — Gym sửa được qua UpdateGymPtRequest. */
    private String phone;
    private String displayName;
    private String bio;
    private String specialization;
    private String serviceArea;
    private Integer experienceYears;
    /** Ảnh đại diện hồ sơ PT (media TRAINER/AVATAR) — xem PtPublicProfileResponse. */
    private String avatarUrl;
    private PtStatus status;
    private String suspensionReason;
    private Long gymProfileId;

    /**
     * Chi nhánh PT được phân công (câu 24 — chi nhánh là đích phân công duy nhất).
     *
     * <p>Không có trường này thì bảng PT của gym trả lời được "gym có ai" nhưng
     * không trả lời được "ai đang ở chi nhánh nào" — mà đó mới là câu gym hỏi khi
     * xếp người. Nạp theo lô ở service, không lazy-load từng dòng.
     */
    private List<BranchRef> branches;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BranchRef {
        private Long id;
        private String name;
        /** Chi nhánh đã tắt vẫn hiện — PT cũ còn dính ở đó là thông tin gym cần thấy. */
        private boolean active;
    }

    public static GymPtResponse of(PtProfile p) {
        return GymPtResponse.builder()
                .id(p.getId())
                .username(p.getUser() != null ? p.getUser().getUsername() : null)
                .email(p.getUser() != null ? p.getUser().getEmail() : null)
                .phone(p.getUser() != null ? p.getUser().getPhone() : null)
                .displayName(p.getDisplayName())
                .bio(p.getBio())
                .specialization(p.getSpecialization())
                .serviceArea(p.getServiceArea())
                .experienceYears(p.getExperienceYears())
                .status(p.getStatus())
                .suspensionReason(p.getSuspensionReason())
                .gymProfileId(p.getGymProfile() != null ? p.getGymProfile().getId() : null)
                .build();
    }
}
