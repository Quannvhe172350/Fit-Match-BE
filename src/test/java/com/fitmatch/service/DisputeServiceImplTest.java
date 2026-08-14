package com.fitmatch.service;

import com.fitmatch.common.enums.DisputeResolution;
import com.fitmatch.common.enums.DisputeStatus;
import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.Role;
import com.fitmatch.common.enums.SettlementStatus;
import com.fitmatch.dto.dispute.ResolveDisputeRequest;
import com.fitmatch.entity.Dispute;
import com.fitmatch.entity.GymProfile;
import com.fitmatch.entity.Ticket;
import com.fitmatch.entity.TicketType;
import com.fitmatch.entity.User;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.repository.DisputeEvidenceRepository;
import com.fitmatch.repository.DisputeRepository;
import com.fitmatch.repository.UserRepository;
import com.fitmatch.service.impl.DisputeServiceImpl;
import com.fitmatch.service.support.DisputeFinancialApplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Luồng xử lý của moderator. Việc MỞ tranh chấp nằm ở
 * {@link TicketDisputeServiceImplTest} — nó cần biết vé/buổi và mức đóng băng
 * theo cấp (câu 34).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DisputeServiceImplTest {

    @Mock private DisputeRepository disputeRepository;
    @Mock private DisputeEvidenceRepository evidenceRepository;
    @Mock private UserRepository userRepository;
    @Mock private DisputeFinancialApplier financialApplier;
    @Mock private AuditService auditService;
    @Mock private com.fitmatch.service.support.NotificationDispatcher notificationDispatcher;
    @InjectMocks private DisputeServiceImpl service;

    private Ticket ticket(SettlementStatus settlement) {
        return Ticket.builder().id(10L)
                .customer(User.builder().id(9L).username("customer1").build())
                .ticketType(TicketType.builder().id(33L).name("Gói 10 ngày").build())
                .gymProfile(GymProfile.builder().id(1L).gymName("Gym A")
                        .user(User.builder().id(2L).username("gym1").build()).build())
                .dayCount(10)
                .payableAmount(BigDecimal.valueOf(1_000_000))
                .settlementStatus(settlement)
                .build();
    }

    private Dispute dispute(DisputeStatus status, SettlementStatus settlement) {
        Dispute d = Dispute.builder().id(1L).status(status)
                .ticket(ticket(settlement))
                .frozenAmount(BigDecimal.valueOf(1_000_000))
                .build();
        when(disputeRepository.findById(1L)).thenReturn(Optional.of(d));
        return d;
    }

    @Test
    void resolve_delegatesToApplierAndSetsResolved() {
        Dispute d = dispute(DisputeStatus.UNDER_REVIEW, SettlementStatus.DISPUTED);

        var res = service.resolve("mod", 1L,
                new ResolveDisputeRequest(DisputeResolution.REFUND_FULL, null, "customer right"));

        verify(financialApplier).apply(d, DisputeResolution.REFUND_FULL, null);
        assertThat(res.getStatus()).isEqualTo(DisputeStatus.RESOLVED);
        assertThat(d.getResolvedAt()).isNotNull();
    }

    /** P1-1.7: tranh chấp đã ESCALATED chỉ Admin được xử — Moderator bị chặn. */
    @Test
    void resolve_escalatedByModerator_forbidden() {
        Dispute d = dispute(DisputeStatus.ESCALATED, SettlementStatus.DISPUTED);
        when(userRepository.findByUsername("mod")).thenReturn(Optional.of(
                User.builder().username("mod").role(Role.ROLE_MODERATOR).build()));

        assertThatThrownBy(() -> service.resolve("mod", 1L,
                new ResolveDisputeRequest(DisputeResolution.REFUND_FULL, null, "x")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
        verify(financialApplier, never()).apply(any(), any(), any());
        assertThat(d.getStatus()).isEqualTo(DisputeStatus.ESCALATED);
    }

    @Test
    void resolve_escalatedByAdmin_succeeds() {
        Dispute d = dispute(DisputeStatus.ESCALATED, SettlementStatus.DISPUTED);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(
                User.builder().username("admin").role(Role.ROLE_ADMIN).build()));

        var res = service.resolve("admin", 1L,
                new ResolveDisputeRequest(DisputeResolution.REFUND_FULL, null, "ok"));

        verify(financialApplier).apply(d, DisputeResolution.REFUND_FULL, null);
        assertThat(res.getStatus()).isEqualTo(DisputeStatus.RESOLVED);
    }

    @Test
    void close_requiresResolved() {
        dispute(DisputeStatus.OPEN, SettlementStatus.HELD);

        assertThatThrownBy(() -> service.close("mod", 1L, "done"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_STATE);
    }

    // ---- P0-4: chống áp tài chính 2 lần qua vòng escalate -> resolve ----

    @Test
    void escalate_onResolvedDispute_throws() {
        Dispute d = dispute(DisputeStatus.RESOLVED, SettlementStatus.PENDING_RELEASE);

        assertThatThrownBy(() -> service.escalate("mod", 1L, "reopen please"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_STATE);
        assertThat(d.getStatus()).isEqualTo(DisputeStatus.RESOLVED);
    }

    @Test
    void escalate_onOpenDispute_succeeds() {
        Dispute d = dispute(DisputeStatus.OPEN, SettlementStatus.HELD);

        service.escalate("mod", 1L, "needs higher review");

        assertThat(d.getStatus()).isEqualTo(DisputeStatus.ESCALATED);
    }

    @Test
    void resolveThenEscalate_appliesFinancialsExactlyOnce() {
        dispute(DisputeStatus.UNDER_REVIEW, SettlementStatus.DISPUTED);

        service.resolve("mod", 1L,
                new ResolveDisputeRequest(DisputeResolution.REFUND_FULL, null, "ok"));
        assertThatThrownBy(() -> service.escalate("mod", 1L, "try again"))
                .isInstanceOf(BusinessException.class);

        verify(financialApplier, org.mockito.Mockito.times(1))
                .apply(any(Dispute.class), any(DisputeResolution.class), any());
    }

    @Test
    void resolve_secondTimeOnResolved_throws() {
        dispute(DisputeStatus.RESOLVED, SettlementStatus.PENDING_RELEASE);

        assertThatThrownBy(() -> service.resolve("mod", 1L,
                new ResolveDisputeRequest(DisputeResolution.RELEASE_TO_GYM, null, "again")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_STATE);
        verify(financialApplier, never()).apply(any(), any(), any());
    }

    /** Người ngoài không đọc được chi tiết tranh chấp — 404 chứ không 403. */
    @Test
    void detail_nonParty_throws404() {
        dispute(DisputeStatus.OPEN, SettlementStatus.HELD);

        assertThatThrownBy(() -> service.detail("stranger", 1L))
                .isInstanceOf(com.fitmatch.exception.ResourceNotFoundException.class);
    }

    @Test
    void detail_customerOfTicket_isAllowed() {
        dispute(DisputeStatus.OPEN, SettlementStatus.HELD);

        assertThat(service.detail("customer1", 1L).getTicketId()).isEqualTo(10L);
    }

    @Test
    void detail_gymOwner_isAllowed() {
        dispute(DisputeStatus.OPEN, SettlementStatus.HELD);

        assertThat(service.detail("gym1", 1L).getTicketId()).isEqualTo(10L);
    }
}
