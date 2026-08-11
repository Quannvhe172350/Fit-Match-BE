package com.fitmatch.entity;

import com.fitmatch.common.enums.WalletOwnerType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Ví của một chủ sở hữu trên nền tảng (UC-061; mở rộng đa chủ ở V61).
 * <p>
 * Tiền booking do nền tảng giữ (RP1) và đi qua các bucket: held (escrow) →
 * pending (chờ hết holding period) → available (rút được). frozen tách riêng
 * khi có dispute/rủi ro hoặc khi đang giữ chỗ cho lệnh rút.
 * {@code @Version} chống race khi đổi số dư.
 * <p>
 * Chủ sở hữu do {@link #ownerType} quyết định, và ĐÚNG MỘT trong hai khoá ngoại
 * dưới đây được set (CHECK constraint {@code chk_wallet_single_owner} ở V61):
 * <ul>
 *   <li>{@link WalletOwnerType#GYM} → {@link #gymProfile}: nhận escrow booking,
 *       giải ngân sau hoa hồng.</li>
 *   <li>{@link WalletOwnerType#CUSTOMER} → {@link #user}: nhận tiền hoàn.</li>
 * </ul>
 * Hai cột riêng thay vì một cột {@code owner_id} đa hình để không mất ràng buộc
 * khoá ngoại ở tầng DB.
 * <p>
 * Schema: wallets(id, owner_type, gym_profile_id FK UNIQUE?, user_id FK UNIQUE?,
 * held_balance, pending_balance, available_balance, frozen_balance, version,
 * + audit).
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

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 20)
    private WalletOwnerType ownerType;

    /** Set khi {@code ownerType == GYM}, null với các loại ví khác. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gym_profile_id", unique = true)
    private GymProfile gymProfile;

    /** Set khi {@code ownerType == CUSTOMER}. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    private User user;

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

    /**
     * User đứng sau ví — người nhận thông báo về lệnh rút và là chủ các tài
     * khoản ngân hàng dùng để chi trả. Truy cập lazy proxy nên chỉ gọi trong
     * transaction.
     */
    public User ownerUser() {
        return switch (ownerType) {
            case GYM -> gymProfile.getUser();
            case CUSTOMER -> user;
        };
    }

    /** Nhãn ngắn cho log/audit, vd {@code GYM#12}. */
    public String ownerLabel() {
        Long ownerId = switch (ownerType) {
            case GYM -> gymProfile.getId();
            case CUSTOMER -> user.getId();
        };
        return ownerType + "#" + ownerId;
    }
}
