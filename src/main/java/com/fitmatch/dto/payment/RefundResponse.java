package com.fitmatch.dto.payment;

import com.fitmatch.common.enums.RefundStatus;
import com.fitmatch.entity.RefundRequest;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Yêu cầu hoàn tiền (UC-055/056). */
@Getter
@Builder
public class RefundResponse {

    private Long id;
    private Long bookingId;
    private BigDecimal amount;
    private String reason;
    private RefundStatus status;
    private String decisionNote;
    private String requestedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static RefundResponse of(RefundRequest r) {
        return RefundResponse.builder()
                .id(r.getId())
                .bookingId(r.getBooking().getId())
                .amount(r.getAmount())
                .reason(r.getReason())
                .status(r.getStatus())
                .decisionNote(r.getDecisionNote())
                .requestedBy(r.getCreatedBy())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}
