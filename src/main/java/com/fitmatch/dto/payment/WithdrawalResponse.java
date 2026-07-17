package com.fitmatch.dto.payment;

import com.fitmatch.common.enums.WithdrawalStatus;
import com.fitmatch.entity.WithdrawalRequest;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Yêu cầu rút tiền của Gym (UC-062). */
@Getter
@Builder
public class WithdrawalResponse {

    private Long id;
    private BigDecimal amount;
    private String bankAccount;
    private String bankName;
    private String accountHolder;
    private WithdrawalStatus status;
    private String reviewNote;
    private String payoutReference;
    private String requestedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static WithdrawalResponse of(WithdrawalRequest r) {
        return WithdrawalResponse.builder()
                .id(r.getId())
                .amount(r.getAmount())
                .bankAccount(r.getBankAccount())
                .bankName(r.getBankName())
                .accountHolder(r.getAccountHolder())
                .status(r.getStatus())
                .reviewNote(r.getReviewNote())
                .payoutReference(r.getPayoutReference())
                .requestedBy(r.getCreatedBy())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}
