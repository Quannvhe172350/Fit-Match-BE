package com.fitmatch.dto.payment;

import com.fitmatch.common.enums.WalletOwnerType;
import com.fitmatch.common.enums.WithdrawalStatus;
import com.fitmatch.entity.WithdrawalRequest;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Yêu cầu rút tiền của Gym / PT / khách hàng (UC-062, đa chủ ví từ V61). */
@Getter
@Builder
public class WithdrawalResponse {

    private Long id;
    private BigDecimal amount;

    /** Mã đối soát nhúng vào nội dung chuyển khoản — Casso khớp giao dịch CHI theo mã này. */
    private String refCode;

    private String bankAccount;
    private String bankName;
    /** Mã BIN VietQR của ngân hàng thụ hưởng; null với lệnh cũ nhập tay. */
    private String bankBin;
    private String accountHolder;

    private WithdrawalStatus status;
    private String reviewNote;
    private String payoutReference;

    /** Link ảnh QR để admin quét và chuyển khoản; có sau khi lệnh được duyệt. */
    private String qrContent;
    private LocalDateTime paidAt;
    /** True khi trạng thái PAID do webhook Casso tự khớp chứ không phải admin bấm tay. */
    private boolean autoMatched;

    /** Loại chủ ví — admin cần phân biệt lệnh của gym, PT hay khách hàng. */
    private WalletOwnerType ownerType;
    /** Tên hiển thị của chủ ví, để admin không phải tra ngược từ username. */
    private String ownerName;

    private String requestedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Chỉ gọi trong transaction: đọc {@code wallet} và chủ sở hữu của nó là lazy
     * proxy nên ngoài transaction sẽ ném LazyInitializationException.
     */
    public static WithdrawalResponse of(WithdrawalRequest r) {
        return WithdrawalResponse.builder()
                .id(r.getId())
                .amount(r.getAmount())
                .refCode(r.getRefCode())
                .bankAccount(r.getBankAccount())
                .bankName(r.getBankName())
                .bankBin(r.getBankBin())
                .accountHolder(r.getAccountHolder())
                .status(r.getStatus())
                .reviewNote(r.getReviewNote())
                .payoutReference(r.getPayoutReference())
                .qrContent(r.getQrContent())
                .paidAt(r.getPaidAt())
                .autoMatched(r.isAutoMatched())
                .ownerType(r.getWallet().getOwnerType())
                .ownerName(ownerNameOf(r))
                .requestedBy(r.getCreatedBy())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }

    private static String ownerNameOf(WithdrawalRequest r) {
        var wallet = r.getWallet();
        return switch (wallet.getOwnerType()) {
            case GYM -> wallet.getGymProfile().getGymName();
            case CUSTOMER -> wallet.getUser().getFullName() != null
                    ? wallet.getUser().getFullName()
                    : wallet.getUser().getUsername();
        };
    }
}
