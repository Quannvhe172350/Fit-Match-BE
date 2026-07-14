package com.fitmatch.dto.booking;

import com.fitmatch.common.enums.CustomerPackageStatus;
import com.fitmatch.entity.CustomerPackage;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/** Gói tập khách đã mua (UC-049/051). */
@Getter
@Builder
public class CustomerPackageResponse {

    private Long id;
    private Long packageId;
    private String packageName;
    private Long gymId;
    private String gymName;
    private Long purchaseBookingId;
    private int sessionsTotal;
    private int sessionsUsed;
    private int sessionsRemaining;
    private LocalDateTime expiresAt;
    private CustomerPackageStatus status;

    public static CustomerPackageResponse of(CustomerPackage cp) {
        return CustomerPackageResponse.builder()
                .id(cp.getId())
                .packageId(cp.getTrainingPackage().getId())
                .packageName(cp.getTrainingPackage().getName())
                .gymId(cp.getTrainingPackage().getGymProfile().getId())
                .gymName(cp.getTrainingPackage().getGymProfile().getGymName())
                .purchaseBookingId(cp.getPurchaseBooking().getId())
                .sessionsTotal(cp.getSessionsTotal())
                .sessionsUsed(cp.getSessionsUsed())
                .sessionsRemaining(Math.max(0, cp.getSessionsTotal() - cp.getSessionsUsed()))
                .expiresAt(cp.getExpiresAt())
                .status(cp.getStatus())
                .build();
    }
}
