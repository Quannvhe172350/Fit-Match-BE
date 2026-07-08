package com.fitmatch.dto.booking;

import com.fitmatch.entity.WaitlistEntry;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaitlistResponse {

    private Long id;
    private String customerUsername;
    private Long serviceId;
    private String serviceName;
    private Long packageId;
    private String packageName;
    private LocalDateTime preferredStart;
    private String note;
    private boolean active;

    public static WaitlistResponse of(WaitlistEntry w) {
        return WaitlistResponse.builder()
                .id(w.getId())
                .customerUsername(w.getCustomer() != null ? w.getCustomer().getUsername() : null)
                .serviceId(w.getGymService() != null ? w.getGymService().getId() : null)
                .serviceName(w.getGymService() != null ? w.getGymService().getName() : null)
                .packageId(w.getTrainingPackage() != null ? w.getTrainingPackage().getId() : null)
                .packageName(w.getTrainingPackage() != null ? w.getTrainingPackage().getName() : null)
                .preferredStart(w.getPreferredStart())
                .note(w.getNote())
                .active(w.isActive())
                .build();
    }
}
