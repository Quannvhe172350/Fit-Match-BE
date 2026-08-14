package com.fitmatch.service;

import com.fitmatch.common.enums.DiscountType;
import com.fitmatch.entity.Ticket;
import com.fitmatch.entity.Voucher;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.repository.VoucherRepository;
import com.fitmatch.service.impl.VoucherServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Voucher trong mô hình vé: gõ mã ở bước mua, tiêu lượt ngay lúc mua. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VoucherServiceImplTest {

    @Mock private VoucherRepository voucherRepository;
    @InjectMocks private VoucherServiceImpl service;

    private Voucher percentVoucher() {
        return Voucher.builder()
                .id(1L).code("SALE10").active(true)
                .discountType(DiscountType.PERCENT).discountValue(BigDecimal.valueOf(10))
                .minBookingAmount(new BigDecimal("500000"))
                .usedCount(0)
                .build();
    }

    private Ticket ticketWith(Voucher v) {
        return Ticket.builder().id(10L).voucher(v).build();
    }

    // ---------- tính giảm giá ----------

    @Test
    void computeDiscount_percent_appliesRate() {
        assertThat(service.computeDiscount(percentVoucher(), new BigDecimal("1000000")))
                .isEqualByComparingTo(new BigDecimal("100000.00"));
    }

    /** Chưa đạt giá trị tối thiểu thì không giảm — không phải lỗi, chỉ là 0đ. */
    @Test
    void computeDiscount_belowMinimum_isZero() {
        assertThat(service.computeDiscount(percentVoucher(), new BigDecimal("100000")))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void computeDiscount_cappedAtMaxDiscount() {
        Voucher v = percentVoucher();
        v.setMaxDiscount(new BigDecimal("50000"));

        assertThat(service.computeDiscount(v, new BigDecimal("1000000")))
                .isEqualByComparingTo(new BigDecimal("50000"));
    }

    /** Không bao giờ giảm quá tổng tiền — nếu không sẽ sinh payable âm. */
    @Test
    void computeDiscount_neverExceedsTotal() {
        Voucher v = Voucher.builder().id(1L).code("BIG").active(true)
                .discountType(DiscountType.FIXED).discountValue(new BigDecimal("999999999"))
                .usedCount(0).build();

        assertThat(service.computeDiscount(v, new BigDecimal("150000")))
                .isEqualByComparingTo(new BigDecimal("150000"));
    }

    // ---------- kiểm tra hiệu lực ----------

    @Test
    void requireUsable_validCode_returnsVoucher() {
        when(voucherRepository.findByCodeIgnoreCase("SALE10")).thenReturn(Optional.of(percentVoucher()));

        assertThat(service.requireUsable("SALE10").getCode()).isEqualTo("SALE10");
    }

    @Test
    void requireUsable_unknownCode_throws() {
        when(voucherRepository.findByCodeIgnoreCase("NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireUsable("NOPE"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Voucher not found");
    }

    @Test
    void requireUsable_inactive_throws() {
        Voucher v = percentVoucher();
        v.setActive(false);
        when(voucherRepository.findByCodeIgnoreCase("SALE10")).thenReturn(Optional.of(v));

        assertThatThrownBy(() -> service.requireUsable("SALE10"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("inactive");
    }

    @Test
    void requireUsable_expired_throws() {
        Voucher v = percentVoucher();
        v.setValidTo(LocalDateTime.now().minusDays(1));
        when(voucherRepository.findByCodeIgnoreCase("SALE10")).thenReturn(Optional.of(v));

        assertThatThrownBy(() -> service.requireUsable("SALE10"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void requireUsable_usageLimitReached_throws() {
        Voucher v = percentVoucher();
        v.setUsageLimit(5);
        v.setUsedCount(5);
        when(voucherRepository.findByCodeIgnoreCase("SALE10")).thenReturn(Optional.of(v));

        assertThatThrownBy(() -> service.requireUsable("SALE10"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("usage limit");
    }

    // ---------- tiêu / trả lượt ----------

    @Test
    void consumeForTicket_incrementsUsedCount() {
        Voucher v = percentVoucher();
        when(voucherRepository.lockById(1L)).thenReturn(Optional.of(v));

        service.consumeForTicket(ticketWith(v));

        assertThat(v.getUsedCount()).isEqualTo(1);
    }

    @Test
    void consumeForTicket_noVoucher_isNoop() {
        service.consumeForTicket(ticketWith(null));

        verify(voucherRepository, never()).lockById(org.mockito.ArgumentMatchers.anyLong());
    }

    /** Lượt cuối cùng bị người khác lấy mất trong lúc mua -> chặn, không âm thầm vượt hạn mức. */
    @Test
    void consumeForTicket_limitReachedMeanwhile_isRejected() {
        Voucher v = percentVoucher();
        v.setUsageLimit(1);
        v.setUsedCount(1);
        when(voucherRepository.lockById(1L)).thenReturn(Optional.of(v));

        assertThatThrownBy(() -> service.consumeForTicket(ticketWith(v)))
                .isInstanceOf(BusinessException.class);
        assertThat(v.getUsedCount()).isEqualTo(1);
    }

    @Test
    void releaseFromTicket_decrementsUsedCount() {
        Voucher v = percentVoucher();
        v.setUsedCount(3);
        when(voucherRepository.lockById(1L)).thenReturn(Optional.of(v));

        service.releaseFromTicket(ticketWith(v));

        assertThat(v.getUsedCount()).isEqualTo(2);
    }

    /** Không ném lỗi vào luồng chính: trả lượt hỏng không được chặn việc huỷ vé. */
    @Test
    void releaseFromTicket_swallowsErrors() {
        Voucher v = percentVoucher();
        when(voucherRepository.lockById(1L)).thenThrow(new IllegalStateException("db down"));

        service.releaseFromTicket(ticketWith(v));
    }

    @Test
    void releaseFromTicket_atZero_staysZero() {
        Voucher v = percentVoucher();
        when(voucherRepository.lockById(1L)).thenReturn(Optional.of(v));

        service.releaseFromTicket(ticketWith(v));

        assertThat(v.getUsedCount()).isZero();
    }
}
