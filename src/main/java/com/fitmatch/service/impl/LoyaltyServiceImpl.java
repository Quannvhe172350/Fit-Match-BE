package com.fitmatch.service.impl;

import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.LoyaltyTxnType;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.loyalty.LoyaltyBalanceResponse;
import com.fitmatch.dto.loyalty.LoyaltyTransactionResponse;
import com.fitmatch.entity.LoyaltyAccount;
import com.fitmatch.entity.LoyaltyTransaction;
import com.fitmatch.entity.User;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.LoyaltyAccountRepository;
import com.fitmatch.repository.LoyaltyTransactionRepository;
import com.fitmatch.repository.UserRepository;
import com.fitmatch.service.LoyaltyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoyaltyServiceImpl implements LoyaltyService {

    /** 10.000đ chi tiêu = 1 điểm. */
    private static final BigDecimal VND_PER_POINT = BigDecimal.valueOf(10_000);
    /** 1 điểm quy đổi = 1.000đ giảm giá. */
    private static final BigDecimal POINT_VALUE_VND = BigDecimal.valueOf(1_000);

    private final LoyaltyAccountRepository accountRepository;
    private final LoyaltyTransactionRepository transactionRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public LoyaltyBalanceResponse balance(String username, Pageable pageable) {
        LoyaltyAccount account = getOrCreate(username);
        PageResponse<LoyaltyTransactionResponse> history = PageResponse.of(
                transactionRepository.findByAccount_IdOrderByIdDesc(account.getId(), pageable),
                LoyaltyTransactionResponse::of);
        return LoyaltyBalanceResponse.builder()
                .pointsBalance(account.getPointsBalance())
                .pointValue(POINT_VALUE_VND)
                .vndPerPoint(VND_PER_POINT)
                .history(history)
                .build();
    }

    // ---------- mô hình vé ----------

    @Override
    @Transactional
    public int availablePoints(String username) {
        return getOrCreate(username).getPointsBalance();
    }

    @Override
    @Transactional
    public void consumeForTicket(com.fitmatch.entity.Ticket ticket) {
        Integer points = ticket.getLoyaltyPointsUsed();
        if (points == null || points <= 0) return;
        LoyaltyAccount account = lock(getOrCreate(ticket.getCustomer().getUsername()).getId());
        if (points > account.getPointsBalance()) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Not enough points to redeem (balance " + account.getPointsBalance() + ")");
        }
        adjustForTicket(account, LoyaltyTxnType.REDEEM, -points, ticket.getId(),
                "Redeemed for ticket #" + ticket.getId());
    }

    /**
     * Câu 35: mốc tích điểm chuyển từ "hoàn tất buổi tập" sang "thanh toán vé
     * thành công". Vé bị huỷ do quá hạn thanh toán không bao giờ đi qua đây nên
     * không phát sinh điểm từ hư không.
     */
    @Override
    @Transactional
    public void earnFromTicket(com.fitmatch.entity.Ticket ticket) {
        try {
            BigDecimal paid = ticket.getPayableAmount();
            if (paid == null || paid.compareTo(BigDecimal.ZERO) <= 0) return;
            int points = paid.divideToIntegralValue(VND_PER_POINT).intValue();
            if (points <= 0) return;
            LoyaltyAccount account = lock(getOrCreate(ticket.getCustomer().getUsername()).getId());
            adjustForTicket(account, LoyaltyTxnType.EARN, points, ticket.getId(),
                    "Earned from ticket #" + ticket.getId());
        } catch (Exception e) {
            log.warn("Loyalty earn failed for ticket {}: {}", ticket.getId(), e.getMessage());
        }
    }

    @Override
    @Transactional
    public void refundToTicket(com.fitmatch.entity.Ticket ticket) {
        try {
            Integer points = ticket.getLoyaltyPointsUsed();
            if (points == null || points <= 0) return;
            LoyaltyAccount account = lock(getOrCreate(ticket.getCustomer().getUsername()).getId());
            adjustForTicket(account, LoyaltyTxnType.REFUND, points, ticket.getId(),
                    "Refunded from cancelled ticket #" + ticket.getId());
            log.info("Loyalty refunded {} points for cancelled ticket {}", points, ticket.getId());
        } catch (Exception e) {
            log.warn("Loyalty refund failed for ticket {}: {}", ticket.getId(), e.getMessage());
        }
    }

    // ---------- helpers ----------

    private LoyaltyAccount getOrCreate(String username) {
        return accountRepository.findByUser_Username(username).orElseGet(() -> {
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new ResourceNotFoundException("User", username));
            return accountRepository.save(LoyaltyAccount.builder().user(user).build());
        });
    }

    private LoyaltyAccount lock(Long id) {
        return accountRepository.lockById(id).orElseThrow();
    }

    /** points dương = tăng, âm = giảm; ghi bút toán snapshot số dư. */
    private void adjustForTicket(LoyaltyAccount account, LoyaltyTxnType type, int points,
                                 Long ticketId, String desc) {
        int newBalance = account.getPointsBalance() + points;
        if (newBalance < 0) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Insufficient loyalty balance");
        }
        account.setPointsBalance(newBalance);
        accountRepository.save(account);
        transactionRepository.save(LoyaltyTransaction.builder()
                .account(account)
                .type(type)
                .points(points)
                .ticketId(ticketId)
                .balanceAfter(newBalance)
                .description(desc)
                .build());
    }

}
