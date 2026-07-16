package com.fitmatch.service;

import com.fitmatch.common.enums.BookingStatus;
import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.LoyaltyTxnType;
import com.fitmatch.entity.Booking;
import com.fitmatch.entity.GymService;
import com.fitmatch.entity.LoyaltyAccount;
import com.fitmatch.entity.User;
import com.fitmatch.entity.Voucher;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.repository.BookingRepository;
import com.fitmatch.repository.LoyaltyAccountRepository;
import com.fitmatch.repository.LoyaltyTransactionRepository;
import com.fitmatch.repository.UserRepository;
import com.fitmatch.service.impl.LoyaltyServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoyaltyServiceImplTest {

    @Mock private LoyaltyAccountRepository accountRepository;
    @Mock private LoyaltyTransactionRepository transactionRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private UserRepository userRepository;
    @InjectMocks private LoyaltyServiceImpl service;

    private final User john = User.builder().username("john").build();

    private Booking draft() {
        return Booking.builder().id(10L).status(BookingStatus.DRAFT).customer(john)
                .gymService(GymService.builder().id(1L).price(new BigDecimal("200000")).build())
                .build();
    }

    @Test
    void earnFromBooking_paid200k_earns20points() {
        Booking b = Booking.builder().id(10L).customer(john).payableAmount(new BigDecimal("200000")).build();
        LoyaltyAccount acc = LoyaltyAccount.builder().id(1L).user(john).pointsBalance(0).build();
        when(accountRepository.findByUser_Username("john")).thenReturn(Optional.of(acc));
        when(accountRepository.lockById(1L)).thenReturn(Optional.of(acc));
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.earnFromBooking(b);

        // 200000 / 10000 = 20 điểm.
        assertThat(acc.getPointsBalance()).isEqualTo(20);
    }

    @Test
    void applyToBooking_setsDiscountAndClearsVoucher() {
        Booking b = draft();
        b.setVoucher(Voucher.builder().id(9L).code("X").build());
        LoyaltyAccount acc = LoyaltyAccount.builder().id(1L).user(john).pointsBalance(50).build();
        when(bookingRepository.findByIdAndCustomer_Username(10L, "john")).thenReturn(Optional.of(b));
        when(accountRepository.findByUser_Username("john")).thenReturn(Optional.of(acc));
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // 30 điểm * 1000 = 30000 giảm.
        var res = service.applyToBooking("john", 10L, 30);

        assertThat(res.getDiscountAmount()).isEqualByComparingTo("30000");
        assertThat(res.getLoyaltyPointsUsed()).isEqualTo(30);
        assertThat(b.getVoucher()).isNull();
    }

    @Test
    void applyToBooking_notEnoughPoints_throws() {
        Booking b = draft();
        LoyaltyAccount acc = LoyaltyAccount.builder().id(1L).user(john).pointsBalance(5).build();
        when(bookingRepository.findByIdAndCustomer_Username(10L, "john")).thenReturn(Optional.of(b));
        when(accountRepository.findByUser_Username("john")).thenReturn(Optional.of(acc));

        assertThatThrownBy(() -> service.applyToBooking("john", 10L, 30))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_STATE);
    }

    @Test
    void refundToBooking_creditsPointsBack() {
        Booking b = draft();
        b.setLoyaltyPointsUsed(30);
        LoyaltyAccount acc = LoyaltyAccount.builder().id(1L).user(john).pointsBalance(20).build();
        when(accountRepository.findByUser_Username("john")).thenReturn(Optional.of(acc));
        when(accountRepository.lockById(1L)).thenReturn(Optional.of(acc));
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.refundToBooking(b);

        // 20 + 30 điểm hoàn = 50.
        assertThat(acc.getPointsBalance()).isEqualTo(50);
        ArgumentCaptor<com.fitmatch.entity.LoyaltyTransaction> cap =
                ArgumentCaptor.forClass(com.fitmatch.entity.LoyaltyTransaction.class);
        verify(transactionRepository).save(cap.capture());
        assertThat(cap.getValue().getType()).isEqualTo(LoyaltyTxnType.REFUND);
        assertThat(cap.getValue().getPoints()).isEqualTo(30);
    }

    @Test
    void refundToBooking_noPointsUsed_noop() {
        Booking b = draft(); // loyaltyPointsUsed == null

        service.refundToBooking(b);

        verify(accountRepository, org.mockito.Mockito.never()).save(any());
        verify(transactionRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void consumeAtCheckout_deductsPoints() {
        Booking b = draft();
        b.setLoyaltyPointsUsed(30);
        LoyaltyAccount acc = LoyaltyAccount.builder().id(1L).user(john).pointsBalance(50).build();
        when(accountRepository.findByUser_Username("john")).thenReturn(Optional.of(acc));
        when(accountRepository.lockById(1L)).thenReturn(Optional.of(acc));
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.consumeAtCheckout(b);

        assertThat(acc.getPointsBalance()).isEqualTo(20);
        ArgumentCaptor<com.fitmatch.entity.LoyaltyTransaction> cap =
                ArgumentCaptor.forClass(com.fitmatch.entity.LoyaltyTransaction.class);
        verify(transactionRepository).save(cap.capture());
        assertThat(cap.getValue().getType()).isEqualTo(LoyaltyTxnType.REDEEM);
        assertThat(cap.getValue().getPoints()).isEqualTo(-30);
    }
}
