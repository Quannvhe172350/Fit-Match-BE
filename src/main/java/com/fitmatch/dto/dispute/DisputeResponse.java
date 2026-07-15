package com.fitmatch.dto.dispute;

import com.fitmatch.common.enums.DisputeResolution;
import com.fitmatch.common.enums.DisputeStatus;
import com.fitmatch.entity.Dispute;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Hồ sơ tranh chấp (UC-063..068). */
@Getter
@Builder
public class DisputeResponse {

    private Long id;
    private Long bookingId;
    private String customerName;
    private Long gymId;
    private String gymName;
    private Long ptProfileId;
    private String ptName;
    private String openedByRole;
    private String openedBy;
    private String reason;
    private DisputeStatus status;
    private DisputeResolution resolution;
    private BigDecimal refundAmount;
    private BigDecimal frozenAmount;
    private String moderatorNote;
    private LocalDateTime resolvedAt;
    private LocalDateTime createdAt;

    public static DisputeResponse of(Dispute d) {
        var b = d.getBooking();
        return DisputeResponse.builder()
                .id(d.getId())
                .bookingId(b.getId())
                .customerName(b.getCustomer().getUsername())
                .gymId(b.getGymProfile().getId())
                .gymName(b.getGymProfile().getGymName())
                .ptProfileId(b.getPtProfile() != null ? b.getPtProfile().getId() : null)
                .ptName(b.getPtProfile() != null ? b.getPtProfile().getDisplayName() : null)
                .openedByRole(d.getOpenedByRole())
                .openedBy(d.getCreatedBy())
                .reason(d.getReason())
                .status(d.getStatus())
                .resolution(d.getResolution())
                .refundAmount(d.getRefundAmount())
                .frozenAmount(d.getFrozenAmount())
                .moderatorNote(d.getModeratorNote())
                .resolvedAt(d.getResolvedAt())
                .createdAt(d.getCreatedAt())
                .build();
    }
}
