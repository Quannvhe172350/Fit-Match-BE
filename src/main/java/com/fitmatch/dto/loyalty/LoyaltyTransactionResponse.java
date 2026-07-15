package com.fitmatch.dto.loyalty;

import com.fitmatch.common.enums.LoyaltyTxnType;
import com.fitmatch.entity.LoyaltyTransaction;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/** Một bút toán điểm thưởng (UC-073). */
@Getter
@Builder
public class LoyaltyTransactionResponse {

    private Long id;
    private LoyaltyTxnType type;
    private int points;
    private int balanceAfter;
    private Long bookingId;
    private String description;
    private LocalDateTime createdAt;

    public static LoyaltyTransactionResponse of(LoyaltyTransaction t) {
        return LoyaltyTransactionResponse.builder()
                .id(t.getId())
                .type(t.getType())
                .points(t.getPoints())
                .balanceAfter(t.getBalanceAfter())
                .bookingId(t.getBookingId())
                .description(t.getDescription())
                .createdAt(t.getCreatedAt())
                .build();
    }
}
