package com.fitmatch.dto.gym;

import com.fitmatch.entity.GymService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GymServiceResponse {

    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private Integer durationMinutes;
    private boolean active;

    public static GymServiceResponse of(GymService s) {
        return GymServiceResponse.builder()
                .id(s.getId())
                .name(s.getName())
                .description(s.getDescription())
                .price(s.getPrice())
                .durationMinutes(s.getDurationMinutes())
                .active(s.isActive())
                .build();
    }
}
