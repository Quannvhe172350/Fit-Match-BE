package com.fitmatch.service.impl;

import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.WalletTxnType;
import com.fitmatch.entity.GymProfile;
import com.fitmatch.entity.User;
import com.fitmatch.entity.Wallet;
import com.fitmatch.entity.WalletTransaction;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.WalletRepository;
import com.fitmatch.repository.WalletTransactionRepository;
import com.fitmatch.service.WalletService;
import com.fitmatch.service.support.WalletCreator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final WalletCreator walletCreator;

    @Override
    @Transactional
    public Wallet getOrCreate(GymProfile gymProfile) {
        return walletRepository.findByGymProfile_Id(gymProfile.getId())
                .orElseGet(() -> createOrReuse(
                        () -> walletCreator.createForGym(gymProfile),
                        () -> walletRepository.lockByGymProfileId(gymProfile.getId())));
    }

    @Override
    @Transactional
    public Wallet getOrCreateForCustomer(User user) {
        return walletRepository.findByUser_Id(user.getId())
                .orElseGet(() -> createOrReuse(
                        () -> walletCreator.createForCustomer(user),
                        () -> walletRepository.lockByUserId(user.getId())));
    }

    /**
     * Tạo ví (ở transaction riêng của {@link WalletCreator}) rồi đọc lại bằng
     * truy vấn KHOÁ.
     * <p>
     * Đọc lại kể cả khi tạo thành công, vì ví do transaction bên trong tạo ra là
     * detached với transaction này — trả thẳng ra thì mọi thay đổi số dư sau đó
     * sẽ không được dirty-checking ghi xuống DB. Đọc lại còn xử lý luôn ca thua
     * cuộc đua: ví mà request song song vừa tạo chính là ví ta cần.
     * <p>
     * Đọc lại mà vẫn rỗng nghĩa là lỗi UNIQUE đến từ nguyên nhân khác (vd
     * {@code chk_wallet_single_owner}) — ném lại lỗi gốc thay vì che đi.
     */
    private Wallet createOrReuse(Runnable create, Supplier<Optional<Wallet>> lockedReread) {
        try {
            create.run();
        } catch (DataIntegrityViolationException e) {
            log.debug("Wallet was created by a concurrent request - reusing it", e);
            return lockedReread.get().orElseThrow(() -> e);
        }
        return lockedReread.get().orElseThrow(
                () -> new IllegalStateException("Wallet was just created but cannot be read back"));
    }

    // ------------------------------------------------------------------
    // Thao tác dùng chung cho mọi loại ví
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public void freeze(Long gymProfileId, BigDecimal amount, String description) {
        freezeWallet(lockGym(gymProfileId), amount, description);
    }

    @Override
    @Transactional
    public void unfreeze(Long gymProfileId, BigDecimal amount, String description) {
        unfreezeWallet(lockGym(gymProfileId), amount, description);
    }

    @Override
    @Transactional
    public void freezeWallet(Wallet wallet, BigDecimal amount, String description) {
        Wallet w = lock(wallet);
        positive(amount);
        require(w.getAvailableBalance().compareTo(amount) >= 0, "Available balance is insufficient to freeze");
        w.setAvailableBalance(w.getAvailableBalance().subtract(amount));
        w.setFrozenBalance(w.getFrozenBalance().add(amount));
        record(w, WalletTxnType.FREEZE, amount, null, description);
    }

    @Override
    @Transactional
    public void unfreezeWallet(Wallet wallet, BigDecimal amount, String description) {
        Wallet w = lock(wallet);
        positive(amount);
        require(w.getFrozenBalance().compareTo(amount) >= 0, "Frozen balance is insufficient to unfreeze");
        w.setFrozenBalance(w.getFrozenBalance().subtract(amount));
        w.setAvailableBalance(w.getAvailableBalance().add(amount));
        record(w, WalletTxnType.UNFREEZE, amount, null, description);
    }

    @Override
    @Transactional
    public void reserveForWithdrawal(Wallet wallet, BigDecimal amount) {
        Wallet w = lock(wallet);
        positive(amount);
        require(w.getAvailableBalance().compareTo(amount) >= 0, "Insufficient available balance for withdrawal");
        w.setAvailableBalance(w.getAvailableBalance().subtract(amount));
        w.setFrozenBalance(w.getFrozenBalance().add(amount));
        record(w, WalletTxnType.FREEZE, amount, null, "Reserved for withdrawal request");
    }

    @Override
    @Transactional
    public void payoutWithdrawal(Wallet wallet, BigDecimal amount) {
        Wallet w = lock(wallet);
        positive(amount);
        require(w.getFrozenBalance().compareTo(amount) >= 0, "Frozen reserve is insufficient for payout");
        w.setFrozenBalance(w.getFrozenBalance().subtract(amount));
        record(w, WalletTxnType.WITHDRAWAL, amount, null, "Withdrawal paid out to beneficiary bank account");
    }

    @Override
    @Transactional
    public void cancelWithdrawalReserve(Wallet wallet, BigDecimal amount) {
        Wallet w = lock(wallet);
        positive(amount);
        require(w.getFrozenBalance().compareTo(amount) >= 0, "Frozen reserve is insufficient to release");
        w.setFrozenBalance(w.getFrozenBalance().subtract(amount));
        w.setAvailableBalance(w.getAvailableBalance().add(amount));
        record(w, WalletTxnType.UNFREEZE, amount, null, "Withdrawal rejected - reserve returned");
    }

    // ------------------------------------------------------------------
    // Escrow vé (V73) — bút toán neo vào ticket_id, chỉ áp cho ví Gym
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public void holdForTicket(Long gymProfileId, Long ticketId, BigDecimal amount) {
        Wallet w = lockGym(gymProfileId);
        positive(amount);
        w.setHeldBalance(w.getHeldBalance().add(amount));
        record(w, WalletTxnType.HOLD, amount, ticketId, "Held ticket funds");
    }

    @Override
    @Transactional
    public void refundToCustomerForTicket(Long gymProfileId, User customer, Long ticketId,
                                          BigDecimal amount) {
        positive(amount);
        if (customer == null) {
            Wallet gymOnly = lockGym(gymProfileId);
            require(gymOnly.getHeldBalance().compareTo(amount) >= 0,
                    "Held balance is insufficient for refund");
            gymOnly.setHeldBalance(gymOnly.getHeldBalance().subtract(amount));
            record(gymOnly, WalletTxnType.REFUND, amount, ticketId,
                    "Refund to customer from held funds");
            log.warn("Refund {} for ticket {} has no customer wallet target - held debited only",
                    amount, ticketId);
            return;
        }
        Wallet gym = walletRepository.findByGymProfile_Id(gymProfileId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet for gym", gymProfileId));
        // lockPair theo id tăng dần — giữ nguyên thứ tự khoá để không deadlock với
        // các luồng khác cũng chạm hai ví.
        Wallet[] locked = lockPair(gym, getOrCreateForCustomer(customer));
        Wallet gymWallet = locked[0];
        Wallet customerWallet = locked[1];

        require(gymWallet.getHeldBalance().compareTo(amount) >= 0,
                "Held balance is insufficient for refund");
        gymWallet.setHeldBalance(gymWallet.getHeldBalance().subtract(amount));
        record(gymWallet, WalletTxnType.REFUND, amount, ticketId,
                "Refund to customer from held funds");

        customerWallet.setAvailableBalance(customerWallet.getAvailableBalance().add(amount));
        record(customerWallet, WalletTxnType.REFUND_CREDIT, amount, ticketId,
                "Refund credited from ticket " + ticketId);
    }

    @Override
    @Transactional
    public void moveToPendingForTicket(Long gymProfileId, Long ticketId, BigDecimal amount) {
        Wallet w = lockGym(gymProfileId);
        positive(amount);
        require(w.getHeldBalance().compareTo(amount) >= 0, "Held balance is insufficient to settle");
        w.setHeldBalance(w.getHeldBalance().subtract(amount));
        w.setPendingBalance(w.getPendingBalance().add(amount));
        record(w, WalletTxnType.MOVE_TO_PENDING, amount, ticketId, "Moved to pending settlement");
    }

    @Override
    @Transactional
    public void reverseToHeldForTicket(Long gymProfileId, Long ticketId, BigDecimal amount) {
        Wallet w = lockGym(gymProfileId);
        positive(amount);
        require(w.getPendingBalance().compareTo(amount) >= 0, "Pending balance is insufficient to reverse");
        w.setPendingBalance(w.getPendingBalance().subtract(amount));
        w.setHeldBalance(w.getHeldBalance().add(amount));
        record(w, WalletTxnType.DISPUTE_HOLD, amount, ticketId, "Pulled back to held for dispute");
    }

    @Override
    @Transactional
    public void releaseForTicket(Long gymProfileId, Long ticketId, BigDecimal amount,
                                 BigDecimal commissionPercent) {
        Wallet w = lockGym(gymProfileId);
        positive(amount);
        require(w.getPendingBalance().compareTo(amount) >= 0, "Pending balance is insufficient to release");
        BigDecimal commission = amount.multiply(commissionPercent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal net = amount.subtract(commission);
        w.setPendingBalance(w.getPendingBalance().subtract(amount));
        w.setAvailableBalance(w.getAvailableBalance().add(net));
        record(w, WalletTxnType.RELEASE, net, ticketId,
                "Released to gym (commission " + commissionPercent + "% = " + commission + ")");
        record(w, WalletTxnType.COMMISSION, commission, ticketId, "Platform commission withheld");
    }

    private Wallet lockGym(Long gymProfileId) {
        Wallet w = walletRepository.findByGymProfile_Id(gymProfileId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet for gym", gymProfileId));
        return walletRepository.lockById(w.getId()).orElseThrow();
    }

    private Wallet lock(Wallet wallet) {
        return walletRepository.lockById(wallet.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Wallet", wallet.getId()));
    }

    /**
     * Khoá hai ví rồi trả về theo ĐÚNG thứ tự tham số. Thứ tự khoá thực tế luôn
     * là id tăng dần: hai thao tác đụng cùng cặp ví theo chiều ngược nhau mà khoá
     * theo thứ tự tham số sẽ ôm khoá chéo và deadlock ở DB.
     */
    private Wallet[] lockPair(Wallet a, Wallet b) {
        if (a.getId().equals(b.getId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Cannot move funds within one wallet");
        }
        if (a.getId() < b.getId()) {
            Wallet lockedA = lock(a);
            return new Wallet[]{lockedA, lock(b)};
        }
        Wallet lockedB = lock(b);
        return new Wallet[]{lock(a), lockedB};
    }

    private WalletTransaction record(Wallet w, WalletTxnType type, BigDecimal amount,
                                     Long ticketId, String desc) {
        walletRepository.save(w);
        WalletTransaction txn = walletTransactionRepository.save(WalletTransaction.builder()
                .wallet(w)
                .type(type)
                .amount(amount)
                .ticketId(ticketId)
                .heldAfter(w.getHeldBalance())
                .pendingAfter(w.getPendingBalance())
                .availableAfter(w.getAvailableBalance())
                .frozenAfter(w.getFrozenBalance())
                .description(desc)
                .build());
        log.info("Wallet {} ({}) {} {} (held={}, pending={}, available={}, frozen={})",
                w.getId(), w.getOwnerType(), type, amount, w.getHeldBalance(), w.getPendingBalance(),
                w.getAvailableBalance(), w.getFrozenBalance());
        return txn;
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
