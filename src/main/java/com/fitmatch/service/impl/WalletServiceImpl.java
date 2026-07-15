package com.fitmatch.service.impl;

import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.WalletTxnType;
import com.fitmatch.entity.GymProfile;
import com.fitmatch.entity.Wallet;
import com.fitmatch.entity.WalletTransaction;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.WalletRepository;
import com.fitmatch.repository.WalletTransactionRepository;
import com.fitmatch.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    @Override
    @Transactional
    public Wallet getOrCreate(GymProfile gymProfile) {
        return walletRepository.findByGymProfile_Id(gymProfile.getId())
                .orElseGet(() -> walletRepository.save(Wallet.builder().gymProfile(gymProfile).build()));
    }

    @Override
    @Transactional
    public void hold(Long gymProfileId, Long bookingId, BigDecimal amount) {
        Wallet w = lock(gymProfileId);
        positive(amount);
        w.setHeldBalance(w.getHeldBalance().add(amount));
        record(w, WalletTxnType.HOLD, amount, bookingId, "Held booking funds");
    }

    @Override
    @Transactional
    public void refundFromHeld(Long gymProfileId, Long bookingId, BigDecimal amount) {
        Wallet w = lock(gymProfileId);
        positive(amount);
        require(w.getHeldBalance().compareTo(amount) >= 0, "Held balance is insufficient for refund");
        w.setHeldBalance(w.getHeldBalance().subtract(amount));
        record(w, WalletTxnType.REFUND, amount, bookingId, "Refund to customer from held funds");
    }

    @Override
    @Transactional
    public void moveToPending(Long gymProfileId, Long bookingId, BigDecimal amount) {
        Wallet w = lock(gymProfileId);
        positive(amount);
        require(w.getHeldBalance().compareTo(amount) >= 0, "Held balance is insufficient to settle");
        w.setHeldBalance(w.getHeldBalance().subtract(amount));
        w.setPendingBalance(w.getPendingBalance().add(amount));
        record(w, WalletTxnType.MOVE_TO_PENDING, amount, bookingId, "Moved to pending settlement");
    }

    @Override
    @Transactional
    public void reverseToHeld(Long gymProfileId, Long bookingId, BigDecimal amount) {
        Wallet w = lock(gymProfileId);
        positive(amount);
        require(w.getPendingBalance().compareTo(amount) >= 0, "Pending balance is insufficient to reverse");
        w.setPendingBalance(w.getPendingBalance().subtract(amount));
        w.setHeldBalance(w.getHeldBalance().add(amount));
        record(w, WalletTxnType.DISPUTE_HOLD, amount, bookingId, "Pulled back to held for dispute");
    }

    @Override
    @Transactional
    public void release(Long gymProfileId, Long bookingId, BigDecimal amount, BigDecimal commissionPercent) {
        Wallet w = lock(gymProfileId);
        positive(amount);
        require(w.getPendingBalance().compareTo(amount) >= 0, "Pending balance is insufficient to release");
        BigDecimal commission = amount.multiply(commissionPercent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal net = amount.subtract(commission);
        w.setPendingBalance(w.getPendingBalance().subtract(amount));
        w.setAvailableBalance(w.getAvailableBalance().add(net));
        record(w, WalletTxnType.RELEASE, net, bookingId,
                "Released to gym (commission " + commissionPercent + "% = " + commission + ")");
        // Bút toán hoa hồng nền tảng — không đổi bucket Gym, phục vụ đối soát/báo cáo.
        record(w, WalletTxnType.COMMISSION, commission, bookingId, "Platform commission withheld");
    }

    @Override
    @Transactional
    public void freeze(Long gymProfileId, BigDecimal amount, String description) {
        Wallet w = lock(gymProfileId);
        positive(amount);
        require(w.getAvailableBalance().compareTo(amount) >= 0, "Available balance is insufficient to freeze");
        w.setAvailableBalance(w.getAvailableBalance().subtract(amount));
        w.setFrozenBalance(w.getFrozenBalance().add(amount));
        record(w, WalletTxnType.FREEZE, amount, null, description);
    }

    @Override
    @Transactional
    public void unfreeze(Long gymProfileId, BigDecimal amount, String description) {
        Wallet w = lock(gymProfileId);
        positive(amount);
        require(w.getFrozenBalance().compareTo(amount) >= 0, "Frozen balance is insufficient to unfreeze");
        w.setFrozenBalance(w.getFrozenBalance().subtract(amount));
        w.setAvailableBalance(w.getAvailableBalance().add(amount));
        record(w, WalletTxnType.UNFREEZE, amount, null, description);
    }

    @Override
    @Transactional
    public void reserveForWithdrawal(Long gymProfileId, BigDecimal amount) {
        Wallet w = lock(gymProfileId);
        positive(amount);
        require(w.getAvailableBalance().compareTo(amount) >= 0, "Insufficient available balance for withdrawal");
        w.setAvailableBalance(w.getAvailableBalance().subtract(amount));
        w.setFrozenBalance(w.getFrozenBalance().add(amount));
        record(w, WalletTxnType.FREEZE, amount, null, "Reserved for withdrawal request");
    }

    @Override
    @Transactional
    public void payoutWithdrawal(Long gymProfileId, BigDecimal amount) {
        Wallet w = lock(gymProfileId);
        positive(amount);
        require(w.getFrozenBalance().compareTo(amount) >= 0, "Frozen reserve is insufficient for payout");
        w.setFrozenBalance(w.getFrozenBalance().subtract(amount));
        record(w, WalletTxnType.WITHDRAWAL, amount, null, "Withdrawal paid out to gym bank account");
    }

    @Override
    @Transactional
    public void cancelWithdrawalReserve(Long gymProfileId, BigDecimal amount) {
        Wallet w = lock(gymProfileId);
        positive(amount);
        require(w.getFrozenBalance().compareTo(amount) >= 0, "Frozen reserve is insufficient to release");
        w.setFrozenBalance(w.getFrozenBalance().subtract(amount));
        w.setAvailableBalance(w.getAvailableBalance().add(amount));
        record(w, WalletTxnType.UNFREEZE, amount, null, "Withdrawal rejected - reserve returned");
    }

    private Wallet lock(Long gymProfileId) {
        Wallet w = walletRepository.findByGymProfile_Id(gymProfileId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet for gym", gymProfileId));
        return walletRepository.lockById(w.getId()).orElseThrow();
    }

    private void record(Wallet w, WalletTxnType type, BigDecimal amount, Long bookingId, String desc) {
        walletRepository.save(w);
        walletTransactionRepository.save(WalletTransaction.builder()
                .wallet(w)
                .type(type)
                .amount(amount)
                .bookingId(bookingId)
                .heldAfter(w.getHeldBalance())
                .pendingAfter(w.getPendingBalance())
                .availableAfter(w.getAvailableBalance())
                .frozenAfter(w.getFrozenBalance())
                .description(desc)
                .build());
        log.info("Wallet {} {} {} (held={}, pending={}, available={}, frozen={})",
                w.getId(), type, amount, w.getHeldBalance(), w.getPendingBalance(),
                w.getAvailableBalance(), w.getFrozenBalance());
    }

    private void positive(BigDecimal amount) {
        require(amount != null && amount.compareTo(BigDecimal.ZERO) > 0, "Amount must be positive");
    }

    private void require(boolean cond, String msg) {
        if (!cond) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE, msg);
        }
    }
}
