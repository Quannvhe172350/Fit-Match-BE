package com.fitmatch.service.impl;

import com.fitmatch.common.enums.BookingStatus;
import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.LoyaltyTxnType;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.booking.BookingResponse;
import com.fitmatch.dto.loyalty.LoyaltyBalanceResponse;
import com.fitmatch.dto.loyalty.LoyaltyTransactionResponse;
import com.fitmatch.entity.Booking;
import com.fitmatch.entity.LoyaltyAccount;
import com.fitmatch.entity.LoyaltyTransaction;
import com.fitmatch.entity.User;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.BookingRepository;
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
    private final BookingRepository bookingRepository;
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

    @Override
    @Transactional
    public void earnFromBooking(Booking booking) {
        try {
            BigDecimal paid = booking.getPayableAmount();
            if (paid == null || paid.compareTo(BigDecimal.ZERO) <= 0) return;
            int points = paid.divideToIntegralValue(VND_PER_POINT).intValue();
            if (points <= 0) return;
            LoyaltyAccount account = lock(getOrCreate(booking.getCustomer().getUsername()).getId());
            adjust(account, LoyaltyTxnType.EARN, points, booking.getId(),
                    "Earned from booking #" + booking.getId());
        } catch (Exception e) {
            log.warn("Loyalty earn failed for booking {}: {}", booking.getId(), e.getMessage());
        }
    }

    @Override
    @Transactional
    public BookingResponse applyToBooking(String customerUsername, Long bookingId, int points) {
        Booking booking = requireDraft(customerUsername, bookingId);
        LoyaltyAccount account = getOrCreate(customerUsername);
        if (points > account.getPointsBalance()) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Not enough points (balance " + account.getPointsBalance() + ")");
        }
        BigDecimal total = bookingTotal(booking);
        BigDecimal discount = pointsToDiscount(points, total);
        if (discount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Points do not yield any discount");
        }
        // Loại trừ lẫn nhau với voucher.
        booking.setVoucher(null);
        booking.setLoyaltyPointsUsed(points);
        booking.setDiscountAmount(discount);
        bookingRepository.save(booking);
        return BookingResponse.of(booking);
    }

    @Override
    @Transactional
    public BookingResponse removeFromBooking(String customerUsername, Long bookingId) {
        Booking booking = requireDraft(customerUsername, bookingId);
        booking.setLoyaltyPointsUsed(null);
        booking.setDiscountAmount(null);
        bookingRepository.save(booking);
        return BookingResponse.of(booking);
    }

    @Override
    @Transactional
    public void recomputeDiscount(Booking booking) {
        if (booking.getLoyaltyPointsUsed() == null) {
            booking.setDiscountAmount(null);
            return;
        }
        booking.setDiscountAmount(pointsToDiscount(booking.getLoyaltyPointsUsed(), bookingTotal(booking)));
    }

    @Override
    @Transactional
    public void consumeAtCheckout(Booking booking) {
        if (booking.getLoyaltyPointsUsed() == null) return;
        LoyaltyAccount account = lock(getOrCreate(booking.getCustomer().getUsername()).getId());
        int points = booking.getLoyaltyPointsUsed();
        if (points > account.getPointsBalance()) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Not enough points to redeem (balance " + account.getPointsBalance() + ")");
        }
        adjust(account, LoyaltyTxnType.REDEEM, -points, booking.getId(),
                "Redeemed for booking #" + booking.getId());
    }

    @Override
    @Transactional
    public void refundToBooking(Booking booking) {
        try {
            Integer points = booking.getLoyaltyPointsUsed();
            if (points == null || points <= 0) return;
            LoyaltyAccount account = lock(getOrCreate(booking.getCustomer().getUsername()).getId());
            adjust(account, LoyaltyTxnType.REFUND, points, booking.getId(),
                    "Refunded from cancelled booking #" + booking.getId());
            log.info("Loyalty refunded {} points for cancelled booking {}", points, booking.getId());
        } catch (Exception e) {
            log.warn("Loyalty refund failed for booking {}: {}", booking.getId(), e.getMessage());
        }
    }

    // ---------- helpers ----------

    private BigDecimal pointsToDiscount(int points, BigDecimal total) {
        if (total == null || total.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        return POINT_VALUE_VND.multiply(BigDecimal.valueOf(points)).min(total);
    }

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
    private void adjust(LoyaltyAccount account, LoyaltyTxnType type, int points, Long bookingId, String desc) {
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
                .bookingId(bookingId)
                .balanceAfter(newBalance)
                .description(desc)
                .build());
    }

    private BigDecimal bookingTotal(Booking b) {
        if (b.getGymService() != null) return b.getGymService().getPrice();
        if (b.getTrainingPackage() != null && b.getCustomerPackage() == null) {
            return b.getTrainingPackage().getPrice();
        }
        return BigDecimal.ZERO;
    }

    private Booking requireDraft(String customerUsername, Long bookingId) {
        Booking booking = bookingRepository.findByIdAndCustomer_Username(bookingId, customerUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
        if (booking.getStatus() != BookingStatus.DRAFT) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Points can only be applied to a DRAFT booking");
        }
        if (booking.getCustomerPackage() != null) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Points do not apply to a free package session");
        }
        return booking;
    }
}
