package com.fitmatch.dto.payment;

import com.fitmatch.common.enums.PaymentStatus;
import com.fitmatch.entity.PaymentOrder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentOrderResponse {

    private Long id;
    /** Neo của đơn thanh toán — luôn là một vé. */
    private Long ticketId;
    private String refCode;
    private BigDecimal amount;
    private PaymentStatus status;
    private String qrContent;
    private LocalDateTime paidAt;
    private LocalDateTime expiresAt;

    public static PaymentOrderResponse of(PaymentOrder o) {
        return PaymentOrderResponse.builder()
                .id(o.getId())
                .ticketId(o.getTicket() != null ? o.getTicket().getId() : null)
                .refCode(o.getRefCode())
                .amount(o.getAmount())
                .status(o.getStatus())
                .qrContent(o.getQrContent())
                .paidAt(o.getPaidAt())
                .expiresAt(o.getExpiresAt())
                .build();
    }
}
