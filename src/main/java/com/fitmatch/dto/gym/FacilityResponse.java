package com.fitmatch.dto.gym;

import com.fitmatch.entity.GymFacility;
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
public class FacilityResponse {

    private Long id;
    private String name;
    private String description;
    private Long branchId;
    private String branchName;
    private boolean active;

    public static FacilityResponse of(GymFacility f) {
        return FacilityResponse.builder()
                .id(f.getId())
                .name(f.getName())
                .description(f.getDescription())
                .branchId(f.getGymBranch() != null ? f.getGymBranch().getId() : null)
                .branchName(f.getGymBranch() != null ? f.getGymBranch().getName() : null)
                .active(f.isActive())
                .build();
    }
}
