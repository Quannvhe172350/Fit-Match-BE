package com.fitmatch.service.impl;

import com.fitmatch.common.AuditActions;
import com.fitmatch.common.enums.BookingStatus;
import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.RefundStatus;
import com.fitmatch.common.enums.SettlementStatus;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.payment.RefundResponse;
import com.fitmatch.entity.Booking;
import com.fitmatch.entity.RefundRequest;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.BookingRepository;
import com.fitmatch.repository.RefundRequestRepository;
import com.fitmatch.service.AuditService;
import com.fitmatch.service.RefundService;
import com.fitmatch.service.SettlementService;
import com.fitmatch.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {

    /** Booking ở các trạng thái này mới được mở yêu cầu hoàn tiền thủ công. */
    private static final Set<BookingStatus> REFUNDABLE_STATUSES =
            Set.of(BookingStatus.REJECTED, BookingStatus.CANCELLED, BookingStatus.NO_SHOW);

    private final RefundRequestRepository refundRequestRepository;
    private final BookingRepository bookingRepository;
    private final WalletService walletService;
    private final SettlementService settlementService;
    private final AuditService auditService;

    @Override
    @Transactional
    public RefundResponse createForCustomer(String customerUsername, Long bookingId, String reason) {
        Booking booking = bookingRepository.findByIdAndCustomer_Username(bookingId, customerUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
        return RefundResponse.of(open(booking, reason, true));
    }

    @Override
    @Transactional
    public RefundResponse createByAdmin(Long bookingId, String reason, String actorUsername) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
        return RefundResponse.of(open(booking, reason + " (by " + actorUsername + ")", true));
    }

    @Override
    @Transactional
    public void autoCreate(Booking booking, String reason) {
        if (booking.getSettlementStatus() != SettlementStatus.HELD
                || refundRequestRepository.existsByBooking_IdAndStatus(booking.getId(), RefundStatus.PENDING)) {
            return;
        }
        open(booking, reason, false);
    }

    @Override
    @Transactional
    public RefundResponse approveAndExecute(Long refundId, BigDecimal approvedAmount,
                                            String note, String actorUsername) {
        RefundRequest request = requirePending(refundId);
        Booking booking = request.getBooking();
        BigDecimal refund = approvedAmount != null ? approvedAmount : request.getAmount();
        if (refund.compareTo(request.getAmount()) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Approved amount " + refund + " exceeds requested " + request.getAmount());
        }

        Long gymId = booking.getGymProfile().getId();
        if (refund.compareTo(BigDecimal.ZERO) > 0) {
            walletService.refundFromHeld(gymId, booking.getId(), refund);
        }
        // Phần không hoàn (phí giữ lại theo chính sách) thuộc về Gym — vào pending settlement.
        BigDecimal retained = request.getAmount().subtract(refund);
        if (retained.compareTo(BigDecimal.ZERO) > 0) {
            walletService.moveToPending(gymId, booking.getId(), retained);
            booking.setSettlementStatus(SettlementStatus.PENDING_RELEASE);
            booking.setSettlementAmount(retained);
            booking.setSettlementPendingAt(LocalDateTime.now());
        } else {
            booking.setSettlementStatus(SettlementStatus.REFUNDED);
        }
        bookingRepository.save(booking);

        request.setStatus(RefundStatus.EXECUTED);
        request.setDecisionNote("Refunded " + refund
                + (retained.compareTo(BigDecimal.ZERO) > 0 ? ", gym retained " + retained : "")
                + (note != null && !note.isBlank() ? " — " + note : ""));
        refundRequestRepository.save(request);

        auditService.record(AuditActions.REFUND_EXECUTE, "RefundRequest", refundId,
                "Refund " + refund + " executed by " + actorUsername
                        + " for booking " + booking.getId());
        log.info("Refund {} executed: refund={}, retained={}, booking={}",
                refundId, refund, retained, booking.getId());
        return RefundResponse.of(request);
    }

    @Override
    @Transactional
    public RefundResponse reject(Long refundId, String note, String actorUsername) {
        RefundRequest request = requirePending(refundId);
        request.setStatus(RefundStatus.REJECTED);
        request.setDecisionNote(note);
        refundRequestRepository.save(request);

        Booking booking = request.getBooking();
        if (booking.getSettlementStatus() == SettlementStatus.REFUND_PENDING) {
            booking.setSettlementStatus(SettlementStatus.HELD);
            bookingRepository.save(booking);
        }
        auditService.record(AuditActions.REFUND_REJECT, "RefundRequest", refundId,
                "Refund rejected by " + actorUsername + ": " + note);
        return RefundResponse.of(request);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RefundResponse> listForCustomer(String customerUsername, Pageable pageable) {
        return PageResponse.of(
                refundRequestRepository.findByBooking_Customer_Username(customerUsername, pageable),
                RefundResponse::of);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RefundResponse> listForAdmin(RefundStatus status, Pageable pageable) {
        RefundStatus effective = status != null ? status : RefundStatus.PENDING;
        return PageResponse.of(refundRequestRepository.findByStatus(effective, pageable), RefundResponse::of);
    }

    /**
     * Mở yêu cầu hoàn tiền cho TOÀN BỘ phần đang giữ (bảo toàn: refund + phí giữ
     * lại = held, không để tiền kẹt); strict=true thì báo lỗi khi không đủ điều kiện.
     */
    private RefundRequest open(Booking booking, String reason, boolean strict) {
        if (!REFUNDABLE_STATUSES.contains(booking.getStatus())) {
            if (!strict) return null;
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Refund can only be requested for a REJECTED/CANCELLED/NO_SHOW booking (current: "
                            + booking.getStatus() + ")");
        }
        if (booking.getSettlementStatus() != SettlementStatus.HELD) {
            if (!strict) return null;
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "No held funds for this booking (settlement status: "
                            + booking.getSettlementStatus() + ")");
        }
        if (refundRequestRepository.existsByBooking_IdAndStatus(booking.getId(), RefundStatus.PENDING)) {
            if (!strict) return null;
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "A pending refund request already exists for this booking");
        }

        BigDecimal requested = settlementService.heldAmountOf(booking);
        if (requested == null || requested.compareTo(BigDecimal.ZERO) <= 0) {
            if (!strict) return null;
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Booking has no held amount to refund");
        }

        RefundRequest request = refundRequestRepository.save(RefundRequest.builder()
                .booking(booking)
                .amount(requested)
                .reason(reason)
                .status(RefundStatus.PENDING)
                .build());
        booking.setSettlementStatus(SettlementStatus.REFUND_PENDING);
        bookingRepository.save(booking);
        auditService.record(AuditActions.REFUND_REQUEST_CREATE, "RefundRequest", request.getId(),
                "Refund " + requested + " requested for booking " + booking.getId() + ": " + reason);
        log.info("Refund request {} opened for booking {} ({})", request.getId(), booking.getId(), requested);
        return request;
    }

    private RefundRequest requirePending(Long refundId) {
        RefundRequest request = refundRequestRepository.findById(refundId)
                .orElseThrow(() -> new ResourceNotFoundException("Refund request", refundId));
        if (request.getStatus() != RefundStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Refund request is not PENDING (current: " + request.getStatus() + ")");
        }
        return request;
    }
}
