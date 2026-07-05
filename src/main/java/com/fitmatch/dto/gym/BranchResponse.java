package com.fitmatch.dto.gym;

import com.fitmatch.entity.GymBranch;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BranchResponse {

    private Long id;
    private String name;
    private String address;
    private String city;
    private String phone;
    private String amenities;
    private boolean active;

    public static BranchResponse of(GymBranch b) {
        return BranchResponse.builder()
                .id(b.getId())
                .name(b.getName())
                .address(b.getAddress())
                .city(b.getCity())
                .phone(b.getPhone())
                .amenities(b.getAmenities())
                .active(b.isActive())
                .build();
    }
}
