package com.fitmatch.dto.payment;

import com.fitmatch.common.enums.WalletTxnType;
import com.fitmatch.entity.WalletTransaction;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Một bút toán trong sổ cái ví (UC-061). */
@Getter
@Builder
public class WalletTransactionResponse {

    private Long id;
    private WalletTxnType type;
    private BigDecimal amount;
    private Long bookingId;
    private String description;
    private BigDecimal heldAfter;
    private BigDecimal pendingAfter;
    private BigDecimal availableAfter;
    private BigDecimal frozenAfter;
    private LocalDateTime createdAt;

    public static WalletTransactionResponse of(WalletTransaction t) {
        return WalletTransactionResponse.builder()
                .id(t.getId())
                .type(t.getType())
                .amount(t.getAmount())
                .bookingId(t.getBookingId())
                .description(t.getDescription())
                .heldAfter(t.getHeldAfter())
                .pendingAfter(t.getPendingAfter())
                .availableAfter(t.getAvailableAfter())
                .frozenAfter(t.getFrozenAfter())
                .createdAt(t.getCreatedAt())
                .build();
    }
}
