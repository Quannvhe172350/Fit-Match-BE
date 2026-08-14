package com.fitmatch.service.impl;

import com.fitmatch.common.AuditActions;
import com.fitmatch.common.enums.DisputeStatus;
import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.dispute.DisputeEvidenceRequest;
import com.fitmatch.dto.dispute.DisputeEvidenceResponse;
import com.fitmatch.dto.dispute.DisputeResponse;
import com.fitmatch.dto.dispute.ResolveDisputeRequest;
import com.fitmatch.entity.Dispute;
import com.fitmatch.entity.DisputeEvidence;
import com.fitmatch.entity.User;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.DisputeEvidenceRepository;
import com.fitmatch.repository.DisputeRepository;
import com.fitmatch.repository.UserRepository;
import com.fitmatch.service.AuditService;
import com.fitmatch.service.DisputeService;
import com.fitmatch.service.support.DisputeFinancialApplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class DisputeServiceImpl implements DisputeService {


    private final DisputeRepository disputeRepository;
    private final DisputeEvidenceRepository evidenceRepository;
    private final UserRepository userRepository;
    private final DisputeFinancialApplier financialApplier;
    private final AuditService auditService;
    private final com.fitmatch.service.support.NotificationDispatcher notificationDispatcher;

    // Mở tranh chấp nằm ở TicketDisputeServiceImpl: nó cần biết vé/buổi và công
    // thức đóng băng theo cấp (câu 34). Lớp này chỉ còn luồng xử lý của
    // moderator — bằng chứng, phân công, kết luận, đóng.

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
                    disputeRepository.findByTicket_GymProfile_User_UsernameOrderByIdDesc(username, pageable);
            case ROLE_PT ->
                    disputeRepository.findBySession_PtProfile_User_UsernameOrderByIdDesc(username, pageable);
            default ->
                    disputeRepository.findByTicket_Customer_UsernameOrderByIdDesc(username, pageable);
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
        // P1-1.7 (UC-068): tranh chấp đã ESCALATED nghĩa là chuyển lên cấp cao hơn —
        // chỉ Platform Admin mới được quyết định, Moderator không thể tự escalate rồi tự xử.
        if (dispute.getStatus() == DisputeStatus.ESCALATED && !isAdmin(moderatorUsername)) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "An escalated dispute can only be resolved by a Platform Admin");
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

    /** P1-1.7: actor có phải Platform Admin không (để gác quyền xử tranh chấp đã escalate). */
    private boolean isAdmin(String username) {
        return userRepository.findByUsername(username)
                .map(u -> u.getRole() == com.fitmatch.common.enums.Role.ROLE_ADMIN)
                .orElse(false);
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

    private Dispute requirePartyDispute(String username, Long disputeId) {
        Dispute dispute = requireDispute(disputeId);
        if (!isParty(dispute, username)) {
            throw new ResourceNotFoundException("Dispute", disputeId);
        }
        return dispute;
    }

    /**
     * Khách của vé, chủ gym, và PT của buổi bị tranh chấp. Tranh chấp CẤP VÉ
     * không quy về một PT nào — vé có thể trải nhiều PT khác nhau.
     */
    private boolean isParty(Dispute dispute, String username) {
        var ticket = dispute.getTicket();
        boolean isCustomer = ticket.getCustomer().getUsername().equals(username);
        boolean isGym = ticket.getGymProfile().getUser() != null
                && ticket.getGymProfile().getUser().getUsername().equals(username);
        var session = dispute.getSession();
        boolean isPt = session != null && session.getPtProfile() != null
                && session.getPtProfile().getUser() != null
                && session.getPtProfile().getUser().getUsername().equals(username);
        return isCustomer || isGym || isPt;
    }
}
