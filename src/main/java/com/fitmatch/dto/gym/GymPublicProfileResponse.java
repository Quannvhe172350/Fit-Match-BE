package com.fitmatch.dto.gym;

import com.fitmatch.entity.GymProfile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Góc nhìn công khai của Gym (marketplace UC-18). Không gồm tài liệu nội bộ/lý do từ chối.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GymPublicProfileResponse {

    private Long id;
    private String gymName;
    private String description;
    private String address;
    private String city;
    private String district;
    private String phone;

    /** Điểm đánh giá trung bình (VISIBLE) và số lượt — UC-071; null ở list nếu chưa tính. */
    private java.math.BigDecimal averageRating;
    private Long reviewCount;

    /** Ảnh đại diện (media đầu tiên của gym) — card danh sách marketplace (bug 11). */
    private String coverUrl;

    /** Bug 14: badge "Đã xác minh" phải data-driven — true khi hồ sơ APPROVED. */
    private boolean verified;

    public static GymPublicProfileResponse of(GymProfile g) {
        return GymPublicProfileResponse.builder()
                .id(g.getId())
                .gymName(g.getGymName())
                .description(g.getDescription())
                .address(g.getAddress())
                .city(g.getCity())
                .district(g.getDistrict())
                .phone(g.getPhone())
                .verified(g.getVerificationStatus() == com.fitmatch.common.enums.VerificationStatus.APPROVED)
                .build();
    }

    public static GymPublicProfileResponse of(GymProfile g, java.math.BigDecimal avg, long count) {
        GymPublicProfileResponse r = of(g);
        r.setAverageRating(avg);
        r.setReviewCount(count);
        return r;
    }
}
