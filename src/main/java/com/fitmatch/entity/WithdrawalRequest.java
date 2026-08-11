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
 * Yêu cầu rút tiền / payout từ một ví bất kỳ — Gym, PT hoặc khách hàng
 * (UC-062; mở rộng đa chủ ở V61). Số tiền được giữ khỏi available ngay khi tạo
 * yêu cầu (tránh rút trùng); trả lại nếu bị từ chối.
 * <p>
 * Thông tin ngân hàng là SNAPSHOT tại thời điểm tạo: người dùng sửa hay xoá
 * {@link BankAccount} về sau không được làm sai lệnh đã gửi đi.
 * <p>
 * Schema: withdrawal_requests(id, wallet_id FK, amount, ref_code UNIQUE,
 * bank_account, bank_name, bank_bin, account_holder, status, review_note,
 * payout_reference, qr_content, paid_at, auto_matched, version, + audit).
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

    /**
     * V61 — mã đối soát duy nhất dạng {@code FMW<id><6 hex>}, admin nhập vào nội
     * dung chuyển khoản (đã nhúng sẵn trong QR). Webhook Casso dựa vào mã này để
     * khớp giao dịch CHI trên sao kê với đúng lệnh rút và tự chuyển sang PAID.
     */
    @Column(name = "ref_code", unique = true, length = 32)
    private String refCode;

    @Column(name = "bank_account", nullable = false, length = 50)
    private String bankAccount;

    @Column(name = "bank_name", nullable = false, length = 100)
    private String bankName;

    /** V61 — mã BIN VietQR của ngân hàng thụ hưởng; thiếu thì không dựng được QR. */
    @Column(name = "bank_bin", length = 10)
    private String bankBin;

    @Column(name = "account_holder", nullable = false, length = 150)
    private String accountHolder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private WithdrawalStatus status = WithdrawalStatus.PENDING;

    @Column(name = "review_note", length = 500)
    private String reviewNote;

    /** D-11: mã giao dịch chuyển khoản thực tế — bắt buộc khi mark-paid để đối soát sao kê (V37). */
    @Column(name = "payout_reference", length = 100)
    private String payoutReference;

    /**
     * V61 — link ảnh QR VietQR trỏ tới tài khoản người thụ hưởng, sinh lúc duyệt.
     * Admin quét là ra sẵn số tài khoản, số tiền và nội dung {@link #refCode}.
     */
    @Column(name = "qr_content", length = 500)
    private String qrContent;

    /** V61 — thời điểm chi trả thực tế (khác updated_at vì bản ghi còn bị sửa sau đó). */
    @Column(name = "paid_at")
    private java.time.LocalDateTime paidAt;

    /** V61 — true khi PAID do webhook Casso tự khớp, false khi admin bấm tay. */
    @Column(name = "auto_matched", nullable = false)
    @Builder.Default
    private boolean autoMatched = false;

    /**
     * Optimistic lock (P1 batch 2): chống hai admin cùng mark-paid/approve một
     * yêu cầu rút -> payout trừ frozen hai lần, ăn vào reserve của lệnh khác. Xem V36.
     */
    @jakarta.persistence.Version
    @Column(nullable = false)
    @Builder.Default
    private long version = 0L;
}
