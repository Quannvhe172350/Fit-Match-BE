package com.fitmatch.entity;

import com.fitmatch.common.enums.WithdrawalStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Yêu cầu rút tiền / payout của Gym (UC-062). Số tiền được giữ khỏi available
 * ngay khi tạo yêu cầu (tránh rút trùng); trả lại nếu bị từ chối.
 * Schema: withdrawal_requests(id, wallet_id FK, amount, bank_account, bank_name,
 * account_holder, status, review_note, + audit).
 */
@Entity
@Table(name = "withdrawal_requests", indexes =
        @Index(name = "idx_withdrawal_wallet", columnList = "wallet_id,status"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WithdrawalRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "bank_account", nullable = false, length = 50)
    private String bankAccount;

    @Column(name = "bank_name", nullable = false, length = 100)
    private String bankName;

    @Column(name = "account_holder", nullable = false, length = 150)
    private String accountHolder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private WithdrawalStatus status = WithdrawalStatus.PENDING;

    @Column(name = "review_note", length = 500)
    private String reviewNote;

    /**
     * Optimistic lock (P1 batch 2): chống hai admin cùng mark-paid/approve một
     * yêu cầu rút -> payout trừ frozen hai lần, ăn vào reserve của lệnh khác. Xem V36.
     */
    @jakarta.persistence.Version
    @Column(nullable = false)
    @Builder.Default
    private long version = 0L;
}
