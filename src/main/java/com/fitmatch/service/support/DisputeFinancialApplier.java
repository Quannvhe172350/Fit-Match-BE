package com.fitmatch.service.support;

import com.fitmatch.common.enums.DisputeResolution;
import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.SettlementStatus;
import com.fitmatch.entity.Booking;
import com.fitmatch.entity.Dispute;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.repository.BookingRepository;
import com.fitmatch.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Áp dụng quyết định tranh chấp vào ví/thanh toán (UC-067). Tiền của booking
 * tranh chấp đã được kéo về held khi mở (settlement = DISPUTED), nên mọi kết
 * quả đều thao tác trên held: hoàn cho khách (refundFromHeld) và/hoặc chuyển
 * phần còn lại về Gym (moveToPending). Bảo toàn: refund + giữ lại = held.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DisputeFinancialApplier {

    private final WalletService walletService;
    private final BookingRepository bookingRepository;

    public void apply(Dispute dispute, DisputeResolution resolution, BigDecimal refundAmount) {
        Booking booking = dispute.getBooking();
        BigDecimal held = dispute.getFrozenAmount() != null ? dispute.getFrozenAmount() : BigDecimal.ZERO;
        Long gymId = booking.getGymProfile().getId();

        // Không có tiền giữ (booking miễn phí / chưa thanh toán) -> chỉ ghi nhận quyết định.
        if (held.compareTo(BigDecimal.ZERO) <= 0) {
            booking.setSettlementStatus(SettlementStatus.NONE);
            bookingRepository.save(booking);
            return;
        }

        BigDecimal refund = switch (resolution) {
            case REFUND_FULL, PENALTY -> held;
            case RELEASE_TO_GYM, NO_ACTION -> BigDecimal.ZERO;
            case REFUND_PARTIAL, SPLIT -> {
                if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0
                        || refundAmount.compareTo(held) > 0) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                            "refundAmount must be > 0 and <= held amount " + held);
                }
                yield refundAmount;
            }
        };
        BigDecimal toGym = held.subtract(refund);

        if (refund.compareTo(BigDecimal.ZERO) > 0) {
            walletService.refundFromHeld(gymId, booking.getId(), refund);
        }
        if (toGym.compareTo(BigDecimal.ZERO) > 0) {
            walletService.moveToPending(gymId, booking.getId(), toGym);
            booking.setSettlementStatus(SettlementStatus.PENDING_RELEASE);
            booking.setSettlementAmount(toGym);
            booking.setSettlementPendingAt(LocalDateTime.now());
        } else {
            booking.setSettlementStatus(SettlementStatus.REFUNDED);
            booking.setSettlementAmount(BigDecimal.ZERO);
        }
        bookingRepository.save(booking);
        log.info("Dispute {} applied: resolution={}, refund={}, toGym={}, booking={}",
                dispute.getId(), resolution, refund, toGym, booking.getId());
    }
}
