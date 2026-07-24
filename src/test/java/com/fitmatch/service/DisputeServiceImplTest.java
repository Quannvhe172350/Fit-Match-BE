package com.fitmatch.service;

import com.fitmatch.common.enums.BookingStatus;
import com.fitmatch.common.enums.DisputeResolution;
import com.fitmatch.common.enums.DisputeStatus;
import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.Role;
import com.fitmatch.common.enums.SettlementStatus;
import com.fitmatch.dto.dispute.OpenDisputeRequest;
import com.fitmatch.dto.dispute.ResolveDisputeRequest;
import com.fitmatch.entity.Booking;
import com.fitmatch.entity.Dispute;
import com.fitmatch.entity.GymProfile;
import com.fitmatch.entity.User;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.repository.BookingRepository;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DisputeServiceImplTest {

    @Mock private DisputeRepository disputeRepository;
    @Mock private DisputeEvidenceRepository evidenceRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private UserRepository userRepository;
    @Mock private WalletService walletService;
    @Mock private SettlementService settlementService;
    @Mock private DisputeFinancialApplier financialApplier;
    @Mock private AuditService auditService;
    @Mock private com.fitmatch.service.support.NotificationDispatcher notificationDispatcher;
    @InjectMocks private DisputeServiceImpl service;

    private Booking booking(SettlementStatus settlement, BigDecimal settlementAmount) {
        return Booking.builder().id(10L)
                .customer(User.builder().username("john").role(Role.ROLE_CUSTOMER).build())
                .gymProfile(GymProfile.builder().id(5L).gymName("Gym A").build())
                .status(BookingStatus.COMPLETED)
                .settlementStatus(settlement)
                .settlementAmount(settlementAmount)
                .build();
    }

    @Test
    void open_pendingReleaseBooking_pullsFundsBackToHeldAndMarksDisputed() {
        Booking b = booking(SettlementStatus.PENDING_RELEASE, new BigDecimal("200.00"));
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(b));
        when(disputeRepository.existsByBooking_IdAndStatusIn(eq(10L), anyList())).thenReturn(false);
        when(userRepository.findByUsername("john"))
                .thenReturn(Optional.of(User.builder().username("john").role(Role.ROLE_CUSTOMER).build()));
        when(disputeRepository.save(any(Dispute.class))).thenAnswer(inv -> {
            Dispute d = inv.getArgument(0);
            d.setId(1L);
            return d;
        });

        var res = service.open("john", new OpenDisputeRequest(10L, "service was bad"));

        verify(walletService).reverseToHeld(5L, 10L, new BigDecimal("200.00"));
        assertThat(b.getSettlementStatus()).isEqualTo(SettlementStatus.DISPUTED);
        assertThat(res.getFrozenAmount()).isEqualByComparingTo("200.00");
    }

    @Test
    void open_heldBooking_usesHeldAmountNoReverse() {
        Booking b = booking(SettlementStatus.HELD, null);
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(b));
        when(disputeRepository.existsByBooking_IdAndStatusIn(eq(10L), anyList())).thenReturn(false);
        when(settlementService.heldAmountOf(b)).thenReturn(new BigDecimal("150.00"));
        when(userRepository.findByUsername("john"))
                .thenReturn(Optional.of(User.builder().username("john").role(Role.ROLE_CUSTOMER).build()));
        when(disputeRepository.save(any(Dispute.class))).thenAnswer(inv -> inv.getArgument(0));

        service.open("john", new OpenDisputeRequest(10L, "no show but charged"));

        verify(walletService, never()).reverseToHeld(any(), any(), any());
        assertThat(b.getSettlementStatus()).isEqualTo(SettlementStatus.DISPUTED);
    }

    @Test
    void open_duplicateUnresolved_throws() {
        Booking b = booking(SettlementStatus.HELD, null);
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(b));
        when(disputeRepository.existsByBooking_IdAndStatusIn(eq(10L), anyList())).thenReturn(true);

        assertThatThrownBy(() -> service.open("john", new OpenDisputeRequest(10L, "again")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_STATE);
    }

    @Test
    void open_nonParty_throws404() {
        Booking b = booking(SettlementStatus.HELD, null);
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(b));

        assertThatThrownBy(() -> service.open("stranger", new OpenDisputeRequest(10L, "x")))
                .isInstanceOf(com.fitmatch.exception.ResourceNotFoundException.class);
    }

    // ── D-18 (quyết định 2026-07-17, phương án A): cửa sổ mở tranh chấp N ngày ──

    @Test
    void open_beyondWindow_throws() {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "openWindowDays", 14L);
        Booking b = booking(SettlementStatus.HELD, null);
        b.setEndAt(java.time.LocalDateTime.now().minusDays(15)); // quá hạn 14 ngày
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(b));
        when(disputeRepository.existsByBooking_IdAndStatusIn(eq(10L), anyList())).thenReturn(false);

        assertThatThrownBy(() -> service.open("john", new OpenDisputeRequest(10L, "too late")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_STATE);
    }

    @Test
    void open_withinWindow_succeeds() {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "openWindowDays", 14L);
        Booking b = booking(SettlementStatus.HELD, null);
        b.setEndAt(java.time.LocalDateTime.now().minusDays(13)); // còn trong hạn
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(b));
        when(disputeRepository.existsByBooking_IdAndStatusIn(eq(10L), anyList())).thenReturn(false);
        when(settlementService.heldAmountOf(b)).thenReturn(new BigDecimal("150.00"));
        when(userRepository.findByUsername("john"))
                .thenReturn(Optional.of(User.builder().username("john").role(Role.ROLE_CUSTOMER).build()));
        when(disputeRepository.save(any(Dispute.class))).thenAnswer(inv -> inv.getArgument(0));

        service.open("john", new OpenDisputeRequest(10L, "issue in time"));

        assertThat(b.getSettlementStatus()).isEqualTo(SettlementStatus.DISPUTED);
        // P0-0.5: booking phải được khóa ghi trước khi kiểm tra trùng -> chống mở dispute đồng thời.
        verify(bookingRepository).lockById(10L);
    }

    @Test
    void open_cancelledBooking_windowAnchorsOnUpdatedAt() {
        // REJECTED/CANCELLED: buổi có thể chưa từng diễn ra -> neo theo lúc bị hủy.
        org.springframework.test.util.ReflectionTestUtils.setField(service, "openWindowDays", 14L);
        Booking b = booking(SettlementStatus.HELD, null);
        b.setStatus(BookingStatus.CANCELLED);
        b.setEndAt(java.time.LocalDateTime.now().minusDays(60)); // endAt cũ không được dùng
        b.setUpdatedAt(java.time.LocalDateTime.now().minusDays(2)); // vừa hủy 2 ngày trước
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(b));
        when(disputeRepository.existsByBooking_IdAndStatusIn(eq(10L), anyList())).thenReturn(false);
        when(settlementService.heldAmountOf(b)).thenReturn(new BigDecimal("150.00"));
        when(userRepository.findByUsername("john"))
                .thenReturn(Optional.of(User.builder().username("john").role(Role.ROLE_CUSTOMER).build()));
        when(disputeRepository.save(any(Dispute.class))).thenAnswer(inv -> inv.getArgument(0));

        service.open("john", new OpenDisputeRequest(10L, "cancelled but charged"));

        assertThat(b.getSettlementStatus()).isEqualTo(SettlementStatus.DISPUTED);
    }

    @Test
    void resolve_delegatesToApplierAndSetsResolved() {
        Dispute d = Dispute.builder().id(1L).status(DisputeStatus.UNDER_REVIEW)
                .booking(booking(SettlementStatus.DISPUTED, new BigDecimal("200.00")))
                .frozenAmount(new BigDecimal("200.00"))
                .build();
        when(disputeRepository.findById(1L)).thenReturn(Optional.of(d));

        var res = service.resolve("mod", 1L,
                new ResolveDisputeRequest(DisputeResolution.REFUND_FULL, null, "customer right"));

        verify(financialApplier).apply(d, DisputeResolution.REFUND_FULL, null);
        assertThat(res.getStatus()).isEqualTo(DisputeStatus.RESOLVED);
        assertThat(d.getResolvedAt()).isNotNull();
    }

    @Test
    void resolve_escalatedByModerator_forbidden() {
        // P1-1.7: tranh chấp đã ESCALATED chỉ Admin được xử — Moderator bị chặn.
        Dispute d = Dispute.builder().id(1L).status(DisputeStatus.ESCALATED)
                .booking(booking(SettlementStatus.DISPUTED, new BigDecimal("200.00")))
                .frozenAmount(new BigDecimal("200.00")).build();
        when(disputeRepository.findById(1L)).thenReturn(Optional.of(d));
        when(userRepository.findByUsername("mod")).thenReturn(Optional.of(
                User.builder().username("mod").role(Role.ROLE_MODERATOR).build()));

        assertThatThrownBy(() -> service.resolve("mod", 1L,
                new ResolveDisputeRequest(DisputeResolution.REFUND_FULL, null, "x")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
        verify(financialApplier, org.mockito.Mockito.never()).apply(any(), any(), any());
        assertThat(d.getStatus()).isEqualTo(DisputeStatus.ESCALATED);
    }

    @Test
    void resolve_escalatedByAdmin_succeeds() {
        Dispute d = Dispute.builder().id(1L).status(DisputeStatus.ESCALATED)
                .booking(booking(SettlementStatus.DISPUTED, new BigDecimal("200.00")))
                .frozenAmount(new BigDecimal("200.00")).build();
        when(disputeRepository.findById(1L)).thenReturn(Optional.of(d));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(
                User.builder().username("admin").role(Role.ROLE_ADMIN).build()));

        var res = service.resolve("admin", 1L,
                new ResolveDisputeRequest(DisputeResolution.REFUND_FULL, null, "ok"));

        verify(financialApplier).apply(d, DisputeResolution.REFUND_FULL, null);
        assertThat(res.getStatus()).isEqualTo(DisputeStatus.RESOLVED);
    }

    @Test
    void close_requiresResolved() {
        Dispute d = Dispute.builder().id(1L).status(DisputeStatus.OPEN)
                .booking(booking(SettlementStatus.HELD, null)).build();
        when(disputeRepository.findById(1L)).thenReturn(Optional.of(d));

        assertThatThrownBy(() -> service.close("mod", 1L, "done"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_STATE);
    }

    // ---- P0-4: chống áp tài chính 2 lần qua vòng escalate -> resolve ----

    @Test
    void escalate_onResolvedDispute_throws() {
        Dispute d = Dispute.builder().id(1L).status(DisputeStatus.RESOLVED)
                .booking(booking(SettlementStatus.PENDING_RELEASE, new BigDecimal("200.00")))
                .frozenAmount(new BigDecimal("200.00")).build();
        when(disputeRepository.findById(1L)).thenReturn(Optional.of(d));

        assertThatThrownBy(() -> service.escalate("mod", 1L, "reopen please"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_STATE);
        assertThat(d.getStatus()).isEqualTo(DisputeStatus.RESOLVED);
    }

    @Test
    void escalate_onOpenDispute_succeeds() {
        Dispute d = Dispute.builder().id(1L).status(DisputeStatus.OPEN)
                .booking(booking(SettlementStatus.HELD, null)).build();
        when(disputeRepository.findById(1L)).thenReturn(Optional.of(d));

        service.escalate("mod", 1L, "needs higher review");

        assertThat(d.getStatus()).isEqualTo(DisputeStatus.ESCALATED);
    }

    @Test
    void resolveThenEscalate_appliesFinancialsExactlyOnce() {
        Dispute d = Dispute.builder().id(1L).status(DisputeStatus.UNDER_REVIEW)
                .booking(booking(SettlementStatus.DISPUTED, new BigDecimal("200.00")))
                .frozenAmount(new BigDecimal("200.00")).build();
        when(disputeRepository.findById(1L)).thenReturn(Optional.of(d));

        service.resolve("mod", 1L,
                new ResolveDisputeRequest(DisputeResolution.REFUND_FULL, null, "ok"));
        assertThatThrownBy(() -> service.escalate("mod", 1L, "try again"))
                .isInstanceOf(BusinessException.class);

        verify(financialApplier, org.mockito.Mockito.times(1))
                .apply(any(Dispute.class), any(DisputeResolution.class), any());
    }

    @Test
    void resolve_secondTimeOnResolved_throws() {
        Dispute d = Dispute.builder().id(1L).status(DisputeStatus.RESOLVED)
                .booking(booking(SettlementStatus.PENDING_RELEASE, new BigDecimal("200.00")))
                .frozenAmount(new BigDecimal("200.00")).build();
        when(disputeRepository.findById(1L)).thenReturn(Optional.of(d));

        assertThatThrownBy(() -> service.resolve("mod", 1L,
                new ResolveDisputeRequest(DisputeResolution.RELEASE_TO_GYM, null, "again")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_STATE);
        verify(financialApplier, never()).apply(any(), any(), any());
    }
}
