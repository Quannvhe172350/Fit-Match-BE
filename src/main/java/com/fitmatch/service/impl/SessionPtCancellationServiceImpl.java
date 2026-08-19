package com.fitmatch.service.impl;

import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.PtCancellationStatus;
import com.fitmatch.common.enums.SettlementStatus;
import com.fitmatch.dto.ticket.SessionPtCancellationDto;
import com.fitmatch.entity.SessionPtCancellation;
import com.fitmatch.entity.Ticket;
import com.fitmatch.entity.TrainingSession;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.SessionPtCancellationRepository;
import com.fitmatch.repository.TicketRepository;
import com.fitmatch.repository.TrainingSessionRepository;
import com.fitmatch.service.SessionPtCancellationService;
import com.fitmatch.service.WalletService;
import com.fitmatch.service.support.NotificationDispatcher;
import com.fitmatch.service.support.PtDayRefundCalculator;
import com.fitmatch.service.support.SessionLifecycle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Quyết định §4.1, nửa phía KHÁCH: buổi tập mất PT vì đơn nghỉ được duyệt, và
 * khách chọn đổi PT hay nhận hoàn phụ phí PT của ngày đó.
 *
 * <p>Chỉ hoàn PHỤ PHÍ PT của MỘT ngày, không hoàn tiền vé ngày đó: vé có giá
 * trị cả ngày và khách vẫn vào tập được — thứ khách mất đúng bằng
 * {@code ptSurchargePerDay}. Số tiền đã chiết theo tỉ lệ voucher/điểm đã dùng,
 * xem {@link PtDayRefundCalculator}.
 *
 * <p>Ghi chú về escrow: tiền chỉ rút ra được khi vé còn ở {@code HELD}. Theo
 * quyết định vận hành, vé chỉ rời HELD sau khi khách dùng hết hoặc vé hết hạn —
 * lúc đó không còn buổi SCHEDULED nào để mà mất PT. Nhánh 409 bên dưới là chốt
 * chặn phòng thủ, không phải luồng nghiệp vụ thường gặp.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionPtCancellationServiceImpl implements SessionPtCancellationService {

    private final SessionPtCancellationRepository cancellationRepository;
    private final TrainingSessionRepository sessionRepository;
    private final TicketRepository ticketRepository;
    private final WalletService walletService;
    private final PtDayRefundCalculator refundCalculator;
    private final SessionLifecycle sessionLifecycle;
    private final NotificationDispatcher notificationDispatcher;

    @Override
    @Transactional(readOnly = true)
    public List<SessionPtCancellationDto> myPending(String customerUsername) {
        return sessionRepository
                .findByTicket_Customer_UsernameAndSessionDateBetweenAndStatusInOrderBySessionDateAsc(
                        customerUsername, LocalDate.now(), LocalDate.now().plusYears(1),
                        List.of(com.fitmatch.common.enums.SessionStatus.SCHEDULED))
                .stream()
                .map(s -> cancellationRepository.findFirstByTrainingSession_IdAndStatusOrderByIdDesc(
                        s.getId(), PtCancellationStatus.PENDING_CUSTOMER).orElse(null))
                .filter(java.util.Objects::nonNull)
                .map(c -> SessionPtCancellationDto.of(c,
                        refundCalculator.refundForOneDay(c.getTrainingSession().getTicket())))
                .toList();
    }

    @Override
    @Transactional
    public SessionPtCancellationDto refund(String customerUsername, Long sessionId) {
        TrainingSession session = sessionRepository
                .findByIdAndTicket_Customer_Username(sessionId, customerUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Training session", sessionId));
        SessionPtCancellation cancellation = cancellationRepository
                .findFirstByTrainingSession_IdAndStatusOrderByIdDesc(
                        sessionId, PtCancellationStatus.PENDING_CUSTOMER)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_STATE,
                        "Buổi tập này không có yêu cầu xử lý nào đang chờ bạn quyết định"));

        Ticket ticket = session.getTicket();
        BigDecimal amount = applyRefund(ticket, cancellation, session, false);
        log.info("Customer {} took the refund {} for session {} (cancellation {})",
                customerUsername, amount, sessionId, cancellation.getId());
        return SessionPtCancellationDto.of(cancellation, BigDecimal.ZERO);
    }

    @Override
    @Transactional
    public void markReplaced(Long sessionId) {
        cancellationRepository
                .findFirstByTrainingSession_IdAndStatusOrderByIdDesc(
                        sessionId, PtCancellationStatus.PENDING_CUSTOMER)
                .ifPresent(c -> {
                    c.setStatus(PtCancellationStatus.REPLACED);
                    c.setResolvedAt(LocalDateTime.now());
                    cancellationRepository.save(c);
                    log.info("Session {} got a replacement PT — cancellation {} closed",
                            sessionId, c.getId());
                });
    }

    @Override
    @Transactional
    public int autoResolveDueCancellations() {
        // Tới ngày tập mà khách chưa quyết thì hệ thống hoàn thay. Khách không
        // được thiệt chỉ vì quên thao tác, và buổi không được đi vào DONE trong
        // khi vẫn còn một khoản treo chưa xử lý.
        List<SessionPtCancellation> due = cancellationRepository.findPendingDueBy(
                PtCancellationStatus.PENDING_CUSTOMER, LocalDate.now());
        int resolved = 0;
        for (SessionPtCancellation cancellation : due) {
            TrainingSession session = cancellation.getTrainingSession();
            // Khách đã tự chọn PT khác trong lúc chờ: buổi có PT trở lại thì
            // không còn gì để hoàn.
            if (session.getPtProfile() != null) {
                cancellation.setStatus(PtCancellationStatus.REPLACED);
                cancellation.setResolvedAt(LocalDateTime.now());
                cancellationRepository.save(cancellation);
                resolved++;
                continue;
            }
            try {
                applyRefund(session.getTicket(), cancellation, session, true);
                resolved++;
            } catch (BusinessException e) {
                // Vé đã rời HELD (hiếm — xem javadoc lớp): đóng dòng lại thay vì
                // để nó quay lại mỗi lần job chạy, và ghi log để vận hành thấy.
                log.warn("Cannot auto-refund cancellation {} on session {}: {}",
                        cancellation.getId(), session.getId(), e.getMessage());
                cancellation.setStatus(PtCancellationStatus.REPLACED);
                cancellation.setResolvedAt(LocalDateTime.now());
                cancellationRepository.save(cancellation);
            }
        }
        if (resolved > 0) {
            log.info("Auto-resolved {} pending PT cancellation(s)", resolved);
        }
        return resolved;
    }

    /**
     * Chuyển tiền và đóng quyết định treo. Cập nhật {@code ticket.ptRefundedAmount}
     * TRƯỚC khi ghi ví: đó là con số mà {@code PartialRefundCalculator} trừ đi khi
     * hoàn cả vé về sau, và là chốt chặn duy nhất giữ cho tổng hoàn không vượt
     * số khách đã trả.
     */
    private BigDecimal applyRefund(Ticket ticket, SessionPtCancellation cancellation,
                                   TrainingSession session, boolean auto) {
        if (ticket.getSettlementStatus() != SettlementStatus.HELD) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Không thể hoàn phụ phí HLV: tiền của vé đang ở trạng thái "
                            + ticket.getSettlementStatus()
                            + ". Vui lòng chọn HLV thay thế cho buổi này.");
        }
        BigDecimal amount = refundCalculator.refundForOneDay(ticket);
        if (amount.signum() > 0) {
            BigDecimal already = ticket.getPtRefundedAmount() == null
                    ? BigDecimal.ZERO : ticket.getPtRefundedAmount();
            ticket.setPtRefundedAmount(already.add(amount));
            ticketRepository.save(ticket);
            walletService.refundToCustomerForTicket(ticket.getGymProfile().getId(),
                    ticket.getCustomer(), ticket.getId(), amount);
        }

        cancellation.setStatus(PtCancellationStatus.REFUNDED);
        cancellation.setRefundAmount(amount);
        cancellation.setResolvedAt(LocalDateTime.now());
        cancellationRepository.save(cancellation);

        sessionLifecycle.recordNote(session, (auto ? "Tự động hoàn " : "Khách chọn hoàn ")
                + amount + " đ phụ phí HLV do PT nghỉ (đơn #"
                + cancellation.getLeaveRequest().getId() + ")");
        sessionRepository.save(session);

        if (auto) {
            notificationDispatcher.sessionPtAutoRefunded(session, amount);
        }
        return amount;
    }
}
