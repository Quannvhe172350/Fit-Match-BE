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
    private boolean active;

    public static FacilityResponse of(GymFacility f) {
        return FacilityResponse.builder()
                .id(f.getId())
                .name(f.getName())
                .description(f.getDescription())
                .active(f.isActive())
                .build();
    }
}
