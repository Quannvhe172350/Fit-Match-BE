package com.fitmatch.service;

import com.fitmatch.entity.GymProfile;
import com.fitmatch.entity.Wallet;

import java.math.BigDecimal;

/**
 * Engine sổ cái ví (UC-057..062). Mọi thao tác đổi số dư đều khoá ví
 * (PESSIMISTIC_WRITE), kiểm tra không âm và ghi một bút toán append-only kèm
 * snapshot số dư. RP1: tiền booking do nền tảng giữ, không trả thẳng Gym/PT.
 */
public interface WalletService {

    /** Lấy hoặc tạo ví cho Gym. */
    Wallet getOrCreate(GymProfile gymProfile);

    /** UC-057: giữ tiền booking vào ví nền tảng (held += amount). */
    void hold(Long gymProfileId, Long bookingId, BigDecimal amount);

    /** UC-055/056: hoàn tiền cho khách từ phần đang giữ (held -= amount). */
    void refundFromHeld(Long gymProfileId, Long bookingId, BigDecimal amount);

    /** UC-058: hoàn tất buổi tập — chuyển held sang pending settlement. */
    void moveToPending(Long gymProfileId, Long bookingId, BigDecimal amount);

    /** UC-059: giải ngân pending về available của Gym, trừ hoa hồng nền tảng. */
    void release(Long gymProfileId, Long bookingId, BigDecimal amount, BigDecimal commissionPercent);

    /** UC-060: đóng băng một khoản từ available sang frozen. */
    void freeze(Long gymProfileId, BigDecimal amount, String description);

    /** UC-060: mở băng từ frozen về available. */
    void unfreeze(Long gymProfileId, BigDecimal amount, String description);

    /** UC-062: giữ chỗ available cho yêu cầu rút tiền (available -> frozen). */
    void reserveForWithdrawal(Long gymProfileId, BigDecimal amount);

    /** UC-062: đã chi trả — tiền rời nền tảng (frozen -= amount). */
    void payoutWithdrawal(Long gymProfileId, BigDecimal amount);

    /** UC-062: huỷ giữ chỗ khi từ chối rút tiền (frozen -> available). */
    void cancelWithdrawalReserve(Long gymProfileId, BigDecimal amount);
}
