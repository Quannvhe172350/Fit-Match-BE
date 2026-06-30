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
    private String phone;

    public static GymPublicProfileResponse of(GymProfile g) {
        return GymPublicProfileResponse.builder()
                .id(g.getId())
                .gymName(g.getGymName())
                .description(g.getDescription())
                .address(g.getAddress())
                .city(g.getCity())
                .phone(g.getPhone())
                .build();
    }
}
