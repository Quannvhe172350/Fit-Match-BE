package com.fitmatch.dto.gym;

import com.fitmatch.entity.TrainingPackage;
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
public class TrainingPackageResponse {

    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private Integer sessionCount;
    private Integer validityDays;
    private String usageConditions;
    private Long gymServiceId;
    private String gymServiceName;
    private BookingRulesDto bookingRules;
    private boolean active;

    public static TrainingPackageResponse of(TrainingPackage p) {
        return TrainingPackageResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .description(p.getDescription())
                .price(p.getPrice())
                .sessionCount(p.getSessionCount())
                .validityDays(p.getValidityDays())
                .usageConditions(p.getUsageConditions())
                .gymServiceId(p.getGymService() != null ? p.getGymService().getId() : null)
                .gymServiceName(p.getGymService() != null ? p.getGymService().getName() : null)
                .bookingRules(BookingRulesDto.of(p.getBookingRules()))
                .active(p.isActive())
                .build();
    }
}
