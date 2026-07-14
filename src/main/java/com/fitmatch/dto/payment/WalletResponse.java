package com.fitmatch.dto.payment;

import com.fitmatch.entity.Wallet;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/** Số dư ví Gym theo 4 bucket (UC-061). */
@Getter
@Builder
public class WalletResponse {

    private Long id;
    /** Tiền booking đang giữ escrow, chưa đến hạn (UC-057). */
    private BigDecimal heldBalance;
    /** Đã hoàn tất buổi tập, đang trong holding period (UC-058). */
    private BigDecimal pendingBalance;
    /** Khả dụng để rút (UC-059). */
    private BigDecimal availableBalance;
    /** Bị đóng băng do dispute/rút tiền đang xử lý (UC-060/062). */
    private BigDecimal frozenBalance;

    public static WalletResponse of(Wallet w) {
        return WalletResponse.builder()
                .id(w.getId())
                .heldBalance(w.getHeldBalance())
                .pendingBalance(w.getPendingBalance())
                .availableBalance(w.getAvailableBalance())
                .frozenBalance(w.getFrozenBalance())
                .build();
    }
}
