package com.fitmatch.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Ví của một Gym (UC-061). Tiền booking do nền tảng giữ (RP1) và đi qua các
 * bucket: held (escrow) → pending (chờ hết holding period) → available (rút được).
 * frozen tách riêng khi có dispute/rủi ro. {@code @Version} chống race khi đổi số dư.
 * Schema: wallets(id, gym_profile_id FK UNIQUE, held_balance, pending_balance,
 * available_balance, frozen_balance, version, + audit).
 */
@Entity
@Table(name = "wallets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Wallet extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gym_profile_id", nullable = false, unique = true)
    private GymProfile gymProfile;

    @Column(name = "held_balance", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal heldBalance = BigDecimal.ZERO;

    @Column(name = "pending_balance", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal pendingBalance = BigDecimal.ZERO;

    @Column(name = "available_balance", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal availableBalance = BigDecimal.ZERO;

    @Column(name = "frozen_balance", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal frozenBalance = BigDecimal.ZERO;

    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;
}
