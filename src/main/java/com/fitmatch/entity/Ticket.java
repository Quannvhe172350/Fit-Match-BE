package com.fitmatch.entity;

import com.fitmatch.common.enums.SettlementStatus;
import com.fitmatch.common.enums.TicketKind;
import com.fitmatch.common.enums.TicketStatus;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Vé đã bán — đơn vị escrow của hệ thống. Mọi dòng tiền (payment_orders,
 * refund_requests, disputes, wallet_transactions, settlement) neo vào vé, không
 * neo vào từng buổi tập.
 *
 * <p>Giá và cấu hình được SNAPSHOT tại thời điểm mua ({@link #unitPrice},
 * {@link #dayCount}, {@link #ptSurchargePerDay}, {@link #expiresAt}) — gym đổi
 * bảng giá hoặc admin đổi hạn vé về sau không làm sai vé đã bán.
 * Xem V69__tickets.sql.
 */
@Entity
@Table(name = "tickets", indexes = {
        @Index(name = "idx_tickets_customer", columnList = "customer_id,status"),
        @Index(name = "idx_tickets_branch_status", columnList = "gym_branch_id,status"),
        @Index(name = "idx_tickets_expiry", columnList = "status,expires_at"),
        @Index(name = "idx_tickets_settlement", columnList = "settlement_status,settlement_pending_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ticket extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_type_id", nullable = false)
    private TicketType ticketType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gym_profile_id", nullable = false)
    private GymProfile gymProfile;

    /** Chi nhánh khách đã chọn khi mua — vé chỉ dùng được ở đúng chi nhánh này. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gym_branch_id", nullable = false)
    private GymBranch gymBranch;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketKind kind;

    /** Snapshot số ngày của vé — đổi catalog không làm co/giãn vé đã bán. */
    @Column(name = "day_count", nullable = false)
    private Integer dayCount;

    /**
     * Khách đã trả phụ phí PT cho vé này. Phụ phí tính theo VÉ (tất cả các ngày),
     * nên bỏ PT khỏi một ngày lẻ không sinh hoàn tiền.
     */
    @Column(name = "with_pt", nullable = false)
    @Builder.Default
    private boolean withPt = false;

    /** Snapshot giá vé không kèm PT tại thời điểm mua. */
    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    /** Snapshot phụ phí PT mỗi ngày tại thời điểm mua (null khi mua vé không PT). */
    @Column(name = "pt_surcharge_per_day", precision = 12, scale = 2)
    private BigDecimal ptSurchargePerDay;

    /**
     * unitPrice + (withPt ? ptSurchargePerDay * dayCount : 0) + servicesAmount,
     * trước giảm giá.
     */
    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    /** V82: tổng tiền dịch vụ kèm theo, đã snapshot trong {@link #serviceItems}. */
    @Column(name = "services_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal servicesAmount = BigDecimal.ZERO;

    /** Dịch vụ khách tick thêm lúc mua. Rỗng = vé thuần, không có add-on. */
    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @Builder.Default
    private List<TicketServiceItem> serviceItems = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voucher_id")
    private Voucher voucher;

    @Column(name = "discount_amount", precision = 12, scale = 2)
    private BigDecimal discountAmount;

    /** Câu 14: khách bật toggle là tiêu toàn bộ điểm khả dụng, cap ở tổng tiền. */
    @Column(name = "loyalty_points_used")
    private Integer loyaltyPointsUsed;

    /** Số tiền thực phải chuyển khoản. 0 = điểm/voucher phủ hết -> ACTIVE ngay. */
    @Column(name = "payable_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal payableAmount;

    /**
     * Tổng phụ phí PT đã hoàn lẻ theo TỪNG BUỔI khi PT xin nghỉ (quyết định
     * §4.1). Bắt buộc phải theo dõi: {@code PartialRefundCalculator} hoàn theo
     * {@code payableAmount}, nên nếu hoàn lẻ rồi sau đó hoàn cả vé mà không trừ
     * phần đã hoàn thì tổng hoàn vượt số khách đã trả — và WalletService không
     * bắt được vì nó kiểm held_balance TỔNG của Gym chứ không theo từng vé.
     */
    @Column(name = "pt_refunded_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal ptRefundedAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TicketStatus status = TicketStatus.PENDING_PAYMENT;

    /** Lý do của lần chuyển trạng thái gần nhất. */
    @Column(name = "status_reason", length = 500)
    private String statusReason;

    /** Thời điểm thanh toán thành công — mốc tích điểm thưởng (câu 35). */
    @Column(name = "purchased_at")
    private LocalDateTime purchasedAt;

    /**
     * Câu 32: hạn dùng vé, chốt lúc mua từ platform_ticket_config (snapshot, không
     * đọc động). Quá hạn -> EXPIRED và tiền tự về gym, khách không hoàn được nữa.
     */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /** Ngày tập đầu tiên, ghi khi khách đặt lịch. Null = vé chưa đặt buổi nào. */
    @Column(name = "start_date")
    private LocalDate startDate;

    /**
     * Cờ idempotent: đã hoàn điểm thưởng / trả lại lượt voucher cho vé này khi nó
     * bị huỷ hoặc hoàn tiền. Nhiều đường huỷ đi qua nhưng chỉ hoàn đúng một lần.
     */
    @Column(name = "promo_released", nullable = false)
    @Builder.Default
    private boolean promoReleased = false;

    /** Trạng thái dòng tiền escrow của vé. */
    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_status", nullable = false, length = 20)
    @Builder.Default
    private SettlementStatus settlementStatus = SettlementStatus.NONE;

    @Column(name = "settlement_amount", precision = 14, scale = 2)
    private BigDecimal settlementAmount;

    @Column(name = "settlement_pending_at")
    private LocalDateTime settlementPendingAt;

    /** % hoa hồng chốt khi tiền chuyển sang pending — không áp hồi tố khi admin đổi. */
    @Column(name = "commission_percent", precision = 5, scale = 2)
    private BigDecimal commissionPercent;

    /**
     * Optimistic lock: chống lost-update khi hai luồng chạm cùng vé (webhook
     * thanh toán vs khách huỷ, job hết hạn vs admin duyệt hoàn, double settle).
     */
    @Version
    @Column(nullable = false)
    @Builder.Default
    private long version = 0L;
}
