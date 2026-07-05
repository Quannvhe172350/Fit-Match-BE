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
    private Long categoryId;
    private String categoryName;
    private String eligibilityNotes;
    private boolean active;

    public static GymServiceResponse of(GymService s) {
        return GymServiceResponse.builder()
                .id(s.getId())
                .name(s.getName())
                .description(s.getDescription())
                .price(s.getPrice())
                .durationMinutes(s.getDurationMinutes())
                .categoryId(s.getCategory() != null ? s.getCategory().getId() : null)
                .categoryName(s.getCategory() != null ? s.getCategory().getName() : null)
                .eligibilityNotes(s.getEligibilityNotes())
                .active(s.isActive())
                .build();
    }
}
