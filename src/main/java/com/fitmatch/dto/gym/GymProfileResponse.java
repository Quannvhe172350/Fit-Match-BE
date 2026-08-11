package com.fitmatch.dto.gym;

import com.fitmatch.common.enums.VerificationStatus;
import com.fitmatch.entity.GymProfile;
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
public class GymProfileResponse {

    private Long id;
    private String username;
    private String gymName;
    private String description;
    private String address;
    private String city;
    private String district;
    private String phone;
    /** UC-18 (V55): toạ độ trụ sở — workspace hiển thị lại vị trí đã ghim trên bản đồ. */
    private java.math.BigDecimal latitude;
    private java.math.BigDecimal longitude;
    private String formattedAddress;

    /**
     * V60: toạ độ do chủ gym kéo ghim tay. Form sửa PHẢI đọc và gửi lại cờ này —
     * thiếu nó thì mỗi lần lưu hồ sơ là ghim tay bị hạ cấp về "Google đoán".
     */
    private boolean coordinatesPinned;
    private VerificationStatus verificationStatus;
    private String rejectionReason;
    private String reviewNote;
    /** Bug S2-01: false = Admin đã yêu cầu gym xác minh lại địa chỉ. */
    private boolean addressVerified;
    private String addressReviewNote;
    private boolean active;
    private List<GymDocumentDto> documents;

    public static GymProfileResponse of(GymProfile g, List<GymDocumentDto> documents) {
        return GymProfileResponse.builder()
                .id(g.getId())
                .username(g.getUser() != null ? g.getUser().getUsername() : null)
                .gymName(g.getGymName())
                .description(g.getDescription())
                .address(g.getAddress())
                .city(g.getCity())
                .district(g.getDistrict())
                .phone(g.getPhone())
                .latitude(g.getLatitude())
                .longitude(g.getLongitude())
                .formattedAddress(g.getFormattedAddress())
                .coordinatesPinned(g.isCoordinatesPinned())
                .verificationStatus(g.getVerificationStatus())
                .rejectionReason(g.getRejectionReason())
                .reviewNote(g.getReviewNote())
                .addressVerified(g.isAddressVerified())
                .addressReviewNote(g.getAddressReviewNote())
                .active(g.isActive())
                .documents(documents)
                .build();
    }
}
