package com.fitmatch.service;

import com.fitmatch.common.enums.BookingStatus;
import com.fitmatch.common.enums.DiscountType;
import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.entity.Booking;
import com.fitmatch.entity.GymService;
import com.fitmatch.entity.User;
import com.fitmatch.entity.Voucher;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.repository.BookingRepository;
import com.fitmatch.repository.VoucherRepository;
import com.fitmatch.service.impl.VoucherServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoucherServiceImplTest {

    @Mock private VoucherRepository voucherRepository;
    @Mock private BookingRepository bookingRepository;
    @InjectMocks private VoucherServiceImpl service;

    private Voucher percent(int pct, BigDecimal max) {
        return Voucher.builder().id(1L).code("SALE").active(true)
                .discountType(DiscountType.PERCENT).discountValue(BigDecimal.valueOf(pct))
                .maxDiscount(max).build();
    }

    @Test
    void computeDiscount_percent_capsAtMaxAndTotal() {
        Voucher v = percent(50, new BigDecimal("80.00"));
        // 50% of 200 = 100, capped at maxDiscount 80.
        assertThat(service.computeDiscount(v, new BigDecimal("200.00"))).isEqualByComparingTo("80.00");
    }

    @Test
    void computeDiscount_fixed_notExceedingTotal() {
        Voucher v = Voucher.builder().active(true).discountType(DiscountType.FIXED)
                .discountValue(new BigDecimal("300.00")).build();
        assertThat(service.computeDiscount(v, new BigDecimal("200.00"))).isEqualByComparingTo("200.00");
    }

    @Test
    void computeDiscount_belowMinAmount_zero() {
        Voucher v = Voucher.builder().active(true).discountType(DiscountType.PERCENT)
                .discountValue(BigDecimal.valueOf(10)).minBookingAmount(new BigDecimal("500.00")).build();
        assertThat(service.computeDiscount(v, new BigDecimal("200.00"))).isEqualByComparingTo("0");
    }

    @Test
    void applyToBooking_validVoucher_setsDiscount() {
        Booking booking = Booking.builder().id(10L).status(BookingStatus.DRAFT)
                .customer(User.builder().username("john").build())
                .gymService(GymService.builder().id(1L).price(new BigDecimal("200.00")).build())
                .build();
        when(bookingRepository.findByIdAndCustomer_Username(10L, "john")).thenReturn(Optional.of(booking));
        when(voucherRepository.findByCodeIgnoreCase("SALE")).thenReturn(Optional.of(percent(10, null)));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        var res = service.applyToBooking("john", 10L, "SALE");

        assertThat(res.getDiscountAmount()).isEqualByComparingTo("20.00");
        assertThat(booking.getVoucher()).isNotNull();
    }

    @Test
    void applyToBooking_clearsPreviouslyAppliedLoyaltyPoints() {
        // P1-14: voucher và điểm loại trừ lẫn nhau — áp voucher phải gỡ điểm đã set,
        // nếu không checkout sẽ ưu tiên điểm và âm thầm bỏ qua voucher.
        Booking booking = Booking.builder().id(10L).status(BookingStatus.DRAFT)
                .customer(User.builder().username("john").build())
                .gymService(GymService.builder().id(1L).price(new BigDecimal("200.00")).build())
                .loyaltyPointsUsed(50)
                .build();
        when(bookingRepository.findByIdAndCustomer_Username(10L, "john")).thenReturn(Optional.of(booking));
        when(voucherRepository.findByCodeIgnoreCase("SALE")).thenReturn(Optional.of(percent(10, null)));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        service.applyToBooking("john", 10L, "SALE");

        assertThat(booking.getLoyaltyPointsUsed()).isNull();
        assertThat(booking.getVoucher()).isNotNull();
    }

    @Test
    void applyToBooking_expiredVoucher_throws() {
        Booking booking = Booking.builder().id(10L).status(BookingStatus.DRAFT)
                .customer(User.builder().username("john").build())
                .gymService(GymService.builder().id(1L).price(new BigDecimal("200.00")).build())
                .build();
        Voucher expired = percent(10, null);
        expired.setValidTo(LocalDateTime.now().minusDays(1));
        when(bookingRepository.findByIdAndCustomer_Username(10L, "john")).thenReturn(Optional.of(booking));
        when(voucherRepository.findByCodeIgnoreCase("SALE")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.applyToBooking("john", 10L, "SALE"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_STATE);
    }

    @Test
    void consumeAtCheckout_incrementsUsedCount() {
        Voucher v = percent(10, null);
        v.setUsageLimit(5);
        v.setUsedCount(2);
        Booking booking = Booking.builder().id(10L).voucher(v).build();
        when(voucherRepository.lockById(1L)).thenReturn(Optional.of(v));
        when(voucherRepository.save(any(Voucher.class))).thenAnswer(inv -> inv.getArgument(0));

        service.consumeAtCheckout(booking);

        assertThat(v.getUsedCount()).isEqualTo(3);
    }

    @Test
    void consumeAtCheckout_limitReached_throws() {
        Voucher v = percent(10, null);
        v.setUsageLimit(2);
        v.setUsedCount(2);
        Booking booking = Booking.builder().id(10L).voucher(v).build();
        when(voucherRepository.lockById(1L)).thenReturn(Optional.of(v));

        assertThatThrownBy(() -> service.consumeAtCheckout(booking))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_STATE);
    }
}
