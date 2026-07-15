package com.fitmatch.service.impl;

import com.fitmatch.common.AuditActions;
import com.fitmatch.common.enums.PaymentStatus;
import com.fitmatch.common.enums.SettlementStatus;
import com.fitmatch.entity.Booking;
import com.fitmatch.entity.CommissionConfig;
import com.fitmatch.entity.PaymentOrder;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.BookingRepository;
import com.fitmatch.repository.PaymentOrderRepository;
import com.fitmatch.service.AuditService;
import com.fitmatch.service.CommissionConfigService;
import com.fitmatch.service.SettlementService;
import com.fitmatch.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementServiceImpl implements SettlementService {

    private final BookingRepository bookingRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final WalletService walletService;
    private final CommissionConfigService commissionConfigService;
    private final AuditService auditService;
    private final com.fitmatch.service.support.NotificationDispatcher notificationDispatcher;

    @Override
    public void markHeld(Booking booking) {
        booking.setSettlementStatus(SettlementStatus.HELD);
    }

    @Override
    @Transactional
    public void settleAfterFulfillment(Booking booking, String reason) {
        if (booking.getSettlementStatus() != SettlementStatus.HELD) {
            // Miễn phí (NONE) hoặc đã xử lý — không có gì để chuyển.
            return;
        }
        BigDecimal heldAmount = resolveHeldAmount(booking);
        if (heldAmount == null || heldAmount.compareTo(BigDecimal.ZERO) <= 0) {
            booking.setSettlementStatus(SettlementStatus.NONE);
            return;
        }
        walletService.moveToPending(booking.getGymProfile().getId(), booking.getId(), heldAmount);
        booking.setSettlementStatus(SettlementStatus.PENDING_RELEASE);
        booking.setSettlementAmount(heldAmount);
        booking.setSettlementPendingAt(LocalDateTime.now());
        auditService.record(AuditActions.SETTLEMENT_PENDING, "Booking", booking.getId(),
                "Moved " + heldAmount + " to pending settlement (" + reason + ")");
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> findDueForRelease() {
        int holdDays = commissionConfigService.currentConfig().getSettlementHoldDays();
        LocalDateTime cutoff = LocalDateTime.now().minusDays(holdDays);
        return bookingRepository
                .findBySettlementStatusAndSettlementPendingAtBefore(SettlementStatus.PENDING_RELEASE, cutoff)
                .stream().map(Booking::getId).toList();
    }

    @Override
    @Transactional
    public void releaseOne(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
        if (booking.getSettlementStatus() != SettlementStatus.PENDING_RELEASE) {
            // Idempotent: job có thể chạy trùng — bỏ qua nếu đã release/đổi trạng thái.
            return;
        }
        CommissionConfig config = commissionConfigService.currentConfig();
        walletService.release(booking.getGymProfile().getId(), booking.getId(),
                booking.getSettlementAmount(), config.getCommissionPercent());
        booking.setSettlementStatus(SettlementStatus.RELEASED);
        bookingRepository.save(booking);
        // UC-059: báo Gym tiền đã về ví khả dụng (số ròng sau hoa hồng).
        BigDecimal commission = booking.getSettlementAmount()
                .multiply(config.getCommissionPercent())
                .divide(java.math.BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
        notificationDispatcher.settlementReleased(booking, booking.getSettlementAmount().subtract(commission));
        auditService.record(AuditActions.SETTLEMENT_RELEASE, "Booking", bookingId,
                "Released " + booking.getSettlementAmount() + " to gym (commission "
                        + config.getCommissionPercent() + "%)");
        log.info("Booking {} settlement released ({}), commission {}%",
                bookingId, booking.getSettlementAmount(), config.getCommissionPercent());
    }

    /** Số tiền thực đã giữ = số tiền đơn thanh toán PAID; fallback payableAmount. */
    @Override
    @Transactional(readOnly = true)
    public BigDecimal heldAmountOf(Booking booking) {
        return resolveHeldAmount(booking);
    }

    private BigDecimal resolveHeldAmount(Booking booking) {
        return paymentOrderRepository.findByBooking_Id(booking.getId())
                .filter(o -> o.getStatus() == PaymentStatus.PAID)
                .map(PaymentOrder::getAmount)
                .orElse(booking.getPayableAmount());
    }
}
