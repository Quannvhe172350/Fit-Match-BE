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
    private VerificationStatus verificationStatus;
    private String rejectionReason;
    private String reviewNote;
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
                .verificationStatus(g.getVerificationStatus())
                .rejectionReason(g.getRejectionReason())
                .reviewNote(g.getReviewNote())
                .active(g.isActive())
                .documents(documents)
                .build();
    }
}
