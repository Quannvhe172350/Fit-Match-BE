package com.fitmatch.service.impl;

import com.fitmatch.common.AuditActions;
import com.fitmatch.common.enums.DisputeStatus;
import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.SettlementStatus;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.dispute.DisputeEvidenceRequest;
import com.fitmatch.dto.dispute.DisputeEvidenceResponse;
import com.fitmatch.dto.dispute.DisputeResponse;
import com.fitmatch.dto.dispute.OpenDisputeRequest;
import com.fitmatch.dto.dispute.ResolveDisputeRequest;
import com.fitmatch.entity.Booking;
import com.fitmatch.entity.Dispute;
import com.fitmatch.entity.DisputeEvidence;
import com.fitmatch.entity.User;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.BookingRepository;
import com.fitmatch.repository.DisputeEvidenceRepository;
import com.fitmatch.repository.DisputeRepository;
import com.fitmatch.repository.UserRepository;
import com.fitmatch.service.AuditService;
import com.fitmatch.service.DisputeService;
import com.fitmatch.service.SettlementService;
import com.fitmatch.service.WalletService;
import com.fitmatch.service.support.DisputeFinancialApplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class DisputeServiceImpl implements DisputeService {

    /** Booking có thể mở tranh chấp — đã phát sinh dịch vụ/tiền. */
    private static final Set<com.fitmatch.common.enums.BookingStatus> DISPUTABLE = Set.of(
            com.fitmatch.common.enums.BookingStatus.CONFIRMED,
            com.fitmatch.common.enums.BookingStatus.COMPLETED,
            com.fitmatch.common.enums.BookingStatus.NO_SHOW,
            com.fitmatch.common.enums.BookingStatus.REJECTED,
            com.fitmatch.common.enums.BookingStatus.CANCELLED);

    /** Tranh chấp chưa đóng — dùng cho chống mở trùng. */
    private static final List<DisputeStatus> OPEN_STATES =
            List.of(DisputeStatus.OPEN, DisputeStatus.UNDER_REVIEW, DisputeStatus.ESCALATED);

    private final DisputeRepository disputeRepository;
    private final DisputeEvidenceRepository evidenceRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final WalletService walletService;
    private final SettlementService settlementService;
    private final DisputeFinancialApplier financialApplier;
    private final AuditService auditService;
    private final com.fitmatch.service.support.NotificationDispatcher notificationDispatcher;

    @Override
    @Transactional
    public DisputeResponse open(String username, OpenDisputeRequest request) {
        Booking booking = requireParty(username, request.getBookingId());
        if (!DISPUTABLE.contains(booking.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Dispute can only be opened for a confirmed/completed/no-show/rejected/cancelled booking");
        }
        if (disputeRepository.existsByBooking_IdAndStatusIn(booking.getId(), OPEN_STATES)) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "An unresolved dispute already exists for this booking");
        }

        // UC-063: bảo vệ tiền — kéo pending về held nếu cần, đánh dấu DISPUTED để
        // scheduler không auto-release trong lúc tranh chấp.
        BigDecimal frozen = protectFunds(booking);

        User opener = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", username));
        Dispute dispute = disputeRepository.save(Dispute.builder()
                .booking(booking)
                .openedByRole(opener.getRole().name())
                .reason(request.getReason())
                .status(DisputeStatus.OPEN)
                .frozenAmount(frozen)
                .build());
        notificationDispatcher.disputeOpened(dispute, username);
        auditService.record(AuditActions.DISPUTE_OPEN, "Dispute", dispute.getId(),
                "Opened by " + username + " (" + opener.getRole() + ") for booking " + booking.getId());
        log.info("Dispute {} opened for booking {} (frozen {})", dispute.getId(), booking.getId(), frozen);
        return DisputeResponse.of(dispute);
    }

    /** Kéo tiền của booking về held và đặt settlement DISPUTED; trả về số tiền được bảo vệ. */
    private BigDecimal protectFunds(Booking booking) {
        SettlementStatus s = booking.getSettlementStatus();
        BigDecimal amount = switch (s) {
            case PENDING_RELEASE -> {
                BigDecimal amt = booking.getSettlementAmount() != null
                        ? booking.getSettlementAmount() : BigDecimal.ZERO;
                if (amt.compareTo(BigDecimal.ZERO) > 0) {
                    walletService.reverseToHeld(booking.getGymProfile().getId(), booking.getId(), amt);
                }
                yield amt;
            }
            case HELD, REFUND_PENDING -> settlementService.heldAmountOf(booking);
            default -> BigDecimal.ZERO; // NONE/RELEASED/REFUNDED: không còn tiền để bảo vệ.
        };
        if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
            booking.setSettlementStatus(SettlementStatus.DISPUTED);
            bookingRepository.save(booking);
        }
        return amount != null ? amount : BigDecimal.ZERO;
    }

    @Override
    @Transactional
    public DisputeEvidenceResponse addEvidence(String username, Long disputeId, DisputeEvidenceRequest request) {
        Dispute dispute = requirePartyDispute(username, disputeId);
        if (dispute.getStatus() == DisputeStatus.CLOSED) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Cannot add evidence to a closed dispute");
        }
        DisputeEvidence saved = evidenceRepository.save(DisputeEvidence.builder()
                .dispute(dispute)
                .description(request.getDescription())
                .fileUrl(request.getFileUrl())
                .build());
        return DisputeEvidenceResponse.of(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DisputeEvidenceResponse> evidence(String username, Long disputeId) {
        requirePartyDispute(username, disputeId);
        return listEvidence(disputeId);
    }

    @Override
    @Transactional(readOnly = true)
    public DisputeResponse detail(String username, Long disputeId) {
        return DisputeResponse.of(requirePartyDispute(username, disputeId));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<DisputeResponse> myDisputes(String username, Pageable pageable) {
        User u = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", username));
        var page = switch (u.getRole()) {
            case ROLE_GYM_OPERATOR ->
                    disputeRepository.findByBooking_GymProfile_User_UsernameOrderByIdDesc(username, pageable);
            case ROLE_PT ->
                    disputeRepository.findByBooking_PtProfile_User_UsernameOrderByIdDesc(username, pageable);
            default ->
                    disputeRepository.findByBooking_Customer_UsernameOrderByIdDesc(username, pageable);
        };
        return PageResponse.of(page, DisputeResponse::of);
    }

    // ---------- Moderator/Admin ----------

    @Override
    @Transactional(readOnly = true)
    public PageResponse<DisputeResponse> queue(DisputeStatus status, Pageable pageable) {
        var page = status != null
                ? disputeRepository.findByStatusOrderByIdDesc(status, pageable)
                : disputeRepository.findAllByOrderByIdDesc(pageable);
        return PageResponse.of(page, DisputeResponse::of);
    }

    @Override
    @Transactional
    public DisputeResponse startReview(String moderatorUsername, Long disputeId) {
        Dispute dispute = requireDispute(disputeId);
        if (dispute.getStatus() != DisputeStatus.OPEN && dispute.getStatus() != DisputeStatus.ESCALATED) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Only an OPEN/ESCALATED dispute can move to review (current: " + dispute.getStatus() + ")");
        }
        dispute.setStatus(DisputeStatus.UNDER_REVIEW);
        // D-12 (audit 2026-07-17): claim case — người bấm "Bắt đầu xem xét" là người phụ trách.
        dispute.setAssignedModerator(moderatorUsername);
        auditService.record(AuditActions.DISPUTE_REVIEW, "Dispute", disputeId,
                "Review started by " + moderatorUsername);
        return DisputeResponse.of(dispute);
    }

    @Override
    @Transactional
    public DisputeResponse resolve(String moderatorUsername, Long disputeId, ResolveDisputeRequest request) {
        Dispute dispute = requireDispute(disputeId);
        if (dispute.getStatus() != DisputeStatus.OPEN
                && dispute.getStatus() != DisputeStatus.UNDER_REVIEW
                && dispute.getStatus() != DisputeStatus.ESCALATED) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Dispute is not in a resolvable state (current: " + dispute.getStatus() + ")");
        }
        // UC-067: áp dụng tài chính trên phần held đang bảo vệ.
        financialApplier.apply(dispute, request.getResolution(), request.getRefundAmount());

        dispute.setResolution(request.getResolution());
        dispute.setRefundAmount(request.getRefundAmount());
        dispute.setModeratorNote(request.getNote());
        dispute.setStatus(DisputeStatus.RESOLVED);
        dispute.setResolvedAt(LocalDateTime.now());
        notificationDispatcher.disputeResolved(dispute);
        auditService.record(AuditActions.DISPUTE_RESOLVE, "Dispute", disputeId,
                "Resolved " + request.getResolution() + " by " + moderatorUsername
                        + (request.getRefundAmount() != null ? " (refund " + request.getRefundAmount() + ")" : ""));
        log.info("Dispute {} resolved {} by {}", disputeId, request.getResolution(), moderatorUsername);
        return DisputeResponse.of(dispute);
    }

    @Override
    @Transactional
    public DisputeResponse close(String moderatorUsername, Long disputeId, String note) {
        Dispute dispute = requireDispute(disputeId);
        if (dispute.getStatus() != DisputeStatus.RESOLVED) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Only a RESOLVED dispute can be closed (current: " + dispute.getStatus() + ")");
        }
        dispute.setStatus(DisputeStatus.CLOSED);
        if (note != null) dispute.setModeratorNote(note);
        auditService.record(AuditActions.DISPUTE_CLOSE, "Dispute", disputeId, "Closed by " + moderatorUsername);
        return DisputeResponse.of(dispute);
    }

    @Override
    @Transactional
    public DisputeResponse escalate(String moderatorUsername, Long disputeId, String note) {
        Dispute dispute = requireDispute(disputeId);
        // Chỉ tranh chấp chưa quyết định mới escalate được. Chặn RESOLVED ->
        // ESCALATED để không thể quay lại resolve() và áp tài chính lần hai
        // trên cùng frozenAmount (UC-066/067).
        if (dispute.getStatus() != DisputeStatus.OPEN
                && dispute.getStatus() != DisputeStatus.UNDER_REVIEW) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Only an OPEN/UNDER_REVIEW dispute can be escalated (current: " + dispute.getStatus() + ")");
        }
        dispute.setStatus(DisputeStatus.ESCALATED);
        if (note != null) dispute.setModeratorNote(note);
        auditService.record(AuditActions.DISPUTE_ESCALATE, "Dispute", disputeId,
                "Escalated by " + moderatorUsername + (note != null ? ": " + note : ""));
        return DisputeResponse.of(dispute);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DisputeEvidenceResponse> evidenceForModerator(Long disputeId) {
        requireDispute(disputeId);
        return listEvidence(disputeId);
    }

    @Override
    @Transactional(readOnly = true)
    public DisputeResponse detailForModerator(Long disputeId) {
        return DisputeResponse.of(requireDispute(disputeId));
    }

    // ---------- helpers ----------

    private List<DisputeEvidenceResponse> listEvidence(Long disputeId) {
        return evidenceRepository.findByDispute_IdOrderByIdAsc(disputeId).stream()
                .map(DisputeEvidenceResponse::of).toList();
    }

    private Dispute requireDispute(Long disputeId) {
        return disputeRepository.findById(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException("Dispute", disputeId));
    }

    /** Booking mà user là customer/gym-operator/pt liên quan — 404 nếu không. */
    private Booking requireParty(String username, Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
        if (!isParty(booking, username)) {
            throw new ResourceNotFoundException("Booking", bookingId);
        }
        return booking;
    }

    private Dispute requirePartyDispute(String username, Long disputeId) {
        Dispute dispute = requireDispute(disputeId);
        if (!isParty(dispute.getBooking(), username)) {
            throw new ResourceNotFoundException("Dispute", disputeId);
        }
        return dispute;
    }

    private boolean isParty(Booking booking, String username) {
        boolean isCustomer = booking.getCustomer().getUsername().equals(username);
        boolean isGym = booking.getGymProfile().getUser() != null
                && booking.getGymProfile().getUser().getUsername().equals(username);
        boolean isPt = booking.getPtProfile() != null && booking.getPtProfile().getUser() != null
                && booking.getPtProfile().getUser().getUsername().equals(username);
        return isCustomer || isGym || isPt;
    }
}
