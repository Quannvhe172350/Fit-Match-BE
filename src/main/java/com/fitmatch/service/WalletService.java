package com.fitmatch.service;

import com.fitmatch.entity.GymProfile;
import com.fitmatch.entity.User;
import com.fitmatch.entity.Wallet;

import java.math.BigDecimal;

/**
 * Engine sổ cái ví (UC-057..062). Mọi thao tác đổi số dư đều khoá ví
 * (PESSIMISTIC_WRITE), kiểm tra không âm và ghi một bút toán append-only kèm
 * snapshot số dư. RP1: tiền booking do nền tảng giữ, không trả thẳng Gym/PT.
 * <p>
 * V61 — ví không còn chỉ của Gym. Các thao tác gắn với escrow booking vẫn nhận
 * {@code gymProfileId} vì chúng chỉ có nghĩa với ví Gym; các thao tác dùng chung
 * cho mọi loại ví (đóng băng, rút tiền) nhận thẳng {@link Wallet}.
 */
public interface WalletService {

    /** Lấy hoặc tạo ví cho Gym. */
    Wallet getOrCreate(GymProfile gymProfile);

    /** V61: lấy hoặc tạo ví cho khách hàng. */
    Wallet getOrCreateForCustomer(User user);

    // ---------------------------------------------------------------
    // Escrow booking — chỉ áp dụng cho ví Gym
    // ---------------------------------------------------------------

    /** UC-057: giữ tiền booking vào ví nền tảng (held += amount). */
    void hold(Long gymProfileId, Long bookingId, BigDecimal amount);

    /** UC-055/056: hoàn tiền cho khách từ phần đang giữ (held -= amount). */
    void refundFromHeld(Long gymProfileId, Long bookingId, BigDecimal amount);

    /**
     * V61 — hoàn tiền cho khách VÀ ghi có ngay vào ví khách trong cùng một
     * transaction: held của gym giảm, available của khách tăng. Thay cho
     * {@link #refundFromHeld} ở các luồng biết được khách là ai, để tiền hoàn
     * không còn "bốc hơi" khỏi sổ sách sau khi rời ví gym.
     *
     * @param customer chủ ví nhận tiền; null thì chỉ trừ held như cũ
     */
    void refundToCustomer(Long gymProfileId, User customer, Long bookingId, BigDecimal amount);

    /** UC-058: hoàn tất buổi tập — chuyển held sang pending settlement. */
    void moveToPending(Long gymProfileId, Long bookingId, BigDecimal amount);

    /** UC-063: kéo tiền từ pending về held khi mở tranh chấp — bảo vệ khỏi auto-release. */
    void reverseToHeld(Long gymProfileId, Long bookingId, BigDecimal amount);

    /** UC-059: giải ngân pending về available của Gym, trừ hoa hồng nền tảng. */
    void release(Long gymProfileId, Long bookingId, BigDecimal amount, BigDecimal commissionPercent);

    // ---------------------------------------------------------------
    // Thao tác dùng chung cho mọi loại ví
    // ---------------------------------------------------------------

    /** UC-060: đóng băng một khoản từ available sang frozen. */
    void freeze(Long gymProfileId, BigDecimal amount, String description);

    /** UC-060: mở băng từ frozen về available. */
    void unfreeze(Long gymProfileId, BigDecimal amount, String description);

    /** V61: đóng băng trên ví bất kỳ. */
    void freezeWallet(Wallet wallet, BigDecimal amount, String description);

    /** V61: mở băng trên ví bất kỳ. */
    void unfreezeWallet(Wallet wallet, BigDecimal amount, String description);

    /** UC-062: giữ chỗ available cho yêu cầu rút tiền (available -> frozen). */
    void reserveForWithdrawal(Wallet wallet, BigDecimal amount);

    /** UC-062: đã chi trả — tiền rời nền tảng (frozen -= amount). */
    void payoutWithdrawal(Wallet wallet, BigDecimal amount);

    /** UC-062: huỷ giữ chỗ khi từ chối rút tiền (frozen -> available). */
    void cancelWithdrawalReserve(Wallet wallet, BigDecimal amount);
}
