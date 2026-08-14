package com.fitmatch.entity;

import com.fitmatch.common.enums.LoyaltyTxnType;
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

/**
 * Sổ cái điểm thưởng — append-only (UC-073). points dương với EARN/REFUND, âm
 * với REDEEM; balanceAfter là snapshot số dư sau bút toán.
 * Schema: loyalty_transactions(id, account_id FK, type, points, ticket_id?,
 * balance_after, description, + audit).
 */
@Entity
@Table(name = "loyalty_transactions", indexes =
        @Index(name = "idx_loyalty_txn_account", columnList = "account_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoyaltyTransaction extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private LoyaltyAccount account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private LoyaltyTxnType type;

    @Column(nullable = false)
    private int points;

    /** Vé phát sinh bút toán điểm (V73). */
    @Column(name = "ticket_id")
    private Long ticketId;

    @Column(name = "balance_after", nullable = false)
    private int balanceAfter;

    @Column(length = 255)
    private String description;
}
