package com.fitmatch.entity;

import com.fitmatch.common.enums.WalletTxnType;
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
 * Sổ cái ví — append-only (UC-061). Mỗi bút toán ghi loại, số tiền và snapshot
 * bốn bucket sau khi áp; không bao giờ UPDATE. Actor/thời điểm lấy từ BaseEntity.
 * Schema: wallet_transactions(id, wallet_id FK, type, amount, ticket_id?,
 * held_after, pending_after, available_after, frozen_after, description, + audit).
 */
@Entity
@Table(name = "wallet_transactions", indexes =
        @Index(name = "idx_wallet_txn_wallet", columnList = "wallet_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletTransaction extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WalletTxnType type;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    /** Vé liên quan — neo mới của sổ cái escrow (V73). */
    @Column(name = "ticket_id")
    private Long ticketId;

    @Column(name = "held_after", nullable = false, precision = 14, scale = 2)
    private BigDecimal heldAfter;

    @Column(name = "pending_after", nullable = false, precision = 14, scale = 2)
    private BigDecimal pendingAfter;

    @Column(name = "available_after", nullable = false, precision = 14, scale = 2)
    private BigDecimal availableAfter;

    @Column(name = "frozen_after", nullable = false, precision = 14, scale = 2)
    private BigDecimal frozenAfter;

    @Column(length = 500)
    private String description;
}
