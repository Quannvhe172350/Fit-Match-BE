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
    /** Neo của yêu cầu hoàn — luôn là một vé. */
    private Long ticketId;
    private String ticketTypeName;
    private BigDecimal amount;
    private String reason;
    private RefundStatus status;
    /** Câu 11: FULL hay PARTIAL_ELAPSED. Null khi chưa duyệt. */
    private com.fitmatch.common.enums.RefundMode refundMode;
    private Integer elapsedDays;
    private BigDecimal retainedAmount;
    private String decisionNote;
    private String requestedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static RefundResponse of(RefundRequest r) {
        return RefundResponse.builder()
                .id(r.getId())
                .ticketId(r.getTicket() != null ? r.getTicket().getId() : null)
                .ticketTypeName(r.getTicket() != null ? r.getTicket().getTicketType().getName() : null)
                .amount(r.getAmount())
                .reason(r.getReason())
                .status(r.getStatus())
                .refundMode(r.getRefundMode())
                .elapsedDays(r.getElapsedDays())
                .retainedAmount(r.getRetainedAmount())
                .decisionNote(r.getDecisionNote())
                .requestedBy(r.getCreatedBy())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}
