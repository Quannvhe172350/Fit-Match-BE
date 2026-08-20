package com.fitmatch.service;

import com.fitmatch.common.enums.CatalogStatus;
import com.fitmatch.common.enums.TicketKind;
import com.fitmatch.common.enums.TicketStatus;
import com.fitmatch.common.enums.VerificationStatus;
import com.fitmatch.dto.ticket.TicketPurchaseResponse;
import com.fitmatch.dto.ticket.TicketQuoteRequest;
import com.fitmatch.dto.ticket.TicketQuoteResponse;
import com.fitmatch.entity.GymBranch;
import com.fitmatch.entity.GymProfile;
import com.fitmatch.entity.PlatformTicketConfig;
import com.fitmatch.entity.Ticket;
import com.fitmatch.entity.TicketType;
import com.fitmatch.entity.User;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.repository.GymBranchRepository;
import com.fitmatch.repository.PlatformTicketConfigRepository;
import com.fitmatch.repository.TicketRepository;
import com.fitmatch.repository.TicketTypeRepository;
import com.fitmatch.repository.UserRepository;
import com.fitmatch.service.impl.TicketPurchaseServiceImpl;
import com.fitmatch.service.support.TicketPaymentHandler;
import com.fitmatch.service.support.TicketPriceCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Luồng mua vé: chốt giá, snapshot, và nhánh "điểm phủ hết -> ACTIVE ngay". */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TicketPurchaseServiceImplTest {

    private static final String USERNAME = "customer1";
    private static final Long BRANCH_ID = 22L;
    private static final Long TYPE_ID = 33L;

    @Mock private TicketRepository ticketRepository;
    @Mock private TicketTypeRepository ticketTypeRepository;
    @Mock private com.fitmatch.repository.TicketStatusHistoryRepository ticketStatusHistoryRepository;
    @Mock private com.fitmatch.repository.TrainingSessionRepository trainingSessionRepository;
    @Mock private GymBranchRepository gymBranchRepository;
    @Mock private UserRepository userRepository;
    @Spy private TicketPriceCalculator priceCalculator = new TicketPriceCalculator();
    @Mock private PlatformTicketConfigRepository ticketConfigRepository;
    @Mock private VoucherService voucherService;
    @Mock private LoyaltyService loyaltyService;
    @Mock private PaymentService paymentService;
    @Mock private com.fitmatch.service.support.TicketLifecycle ticketLifecycle;
    @Mock private TicketPaymentHandler ticketPaymentHandler;
    @Mock private com.fitmatch.service.support.TicketPromotionReleaser ticketPromotionReleaser;
    @InjectMocks private TicketPurchaseServiceImpl service;

    private TicketType packageType;

    @BeforeEach
    void setUp() {
        GymProfile gym = GymProfile.builder()
                .id(1L).gymName("Gym A")
                .verificationStatus(VerificationStatus.APPROVED).active(true)
                .user(User.builder().id(2L).username("gym1").build())
                .build();
        packageType = TicketType.builder()
                .id(TYPE_ID).gymProfile(gym).name("Gói 10 ngày")
                .kind(TicketKind.PACKAGE).dayCount(10)
                .price(BigDecimal.valueOf(1_000_000))
                .ptSurchargePerDay(BigDecimal.valueOf(200_000))
                .status(CatalogStatus.PUBLISHED).active(true)
                .build();

        when(ticketTypeRepository.findById(TYPE_ID)).thenReturn(Optional.of(packageType));
        when(ticketTypeRepository.existsByIdAndBranches_GymBranch_Id(TYPE_ID, BRANCH_ID)).thenReturn(true);
        when(gymBranchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(
                GymBranch.builder().id(BRANCH_ID).name("Chi nhánh 1").gymProfile(gym).build()));
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(
                User.builder().id(9L).username(USERNAME).fullName("Khách A").build()));
        when(ticketConfigRepository.findTopByOrderByIdDesc()).thenReturn(Optional.of(
                PlatformTicketConfig.builder().id(1L)
                        .dayTicketExpiryDays(30).packageTicketExpiryDays(90).build()));
        when(loyaltyService.availablePoints(USERNAME)).thenReturn(0);
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> {
            Ticket t = inv.getArgument(0);
            if (t.getId() == null) t.setId(100L);
            return t;
        });
    }

    private TicketQuoteRequest request(boolean withPt) {
        return TicketQuoteRequest.builder()
                .branchId(BRANCH_ID).ticketTypeId(TYPE_ID).withPt(withPt).build();
    }

    @Test
    void quote_packageWithPt_multipliesSurchargeByDayCount() {
        TicketQuoteResponse quote = service.quote(USERNAME, request(true));

        assertThat(quote.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(3_000_000));
        assertThat(quote.getPayableAmount()).isEqualByComparingTo(BigDecimal.valueOf(3_000_000));
        assertThat(quote.getDayCount()).isEqualTo(10);
    }

    /**
     * Mã sai ở màn báo giá KHÔNG được ném lỗi — khách vẫn phải thấy giá vé.
     *
     * <p>Phải hỏi qua {@code checkUsable}, KHÔNG được bắt ngoại lệ của
     * {@code requireUsable}: cả hai đều @Transactional nên ngoại lệ ném ra từ
     * requireUsable đánh dấu transaction rollback-only, bắt xong vẫn vỡ 500 lúc
     * commit. Test này khoá lại đường gọi đúng — verify bên dưới đảm bảo
     * requireUsable không bị dùng nhầm ở màn báo giá.
     */
    @Test
    void quote_invalidVoucher_returnsMessageInsteadOfFailing() {
        when(voucherService.checkUsable("SAI"))
                .thenReturn(new VoucherService.UsableCheck(null, "Mã giảm giá đã hết hạn"));
        TicketQuoteRequest req = request(false);
        req.setVoucherCode("SAI");

        TicketQuoteResponse quote = service.quote(USERNAME, req);

        assertThat(quote.getVoucherMessage()).contains("hết hạn");
        assertThat(quote.getPayableAmount()).isEqualByComparingTo(BigDecimal.valueOf(1_000_000));
        verify(voucherService, never()).requireUsable(any());
    }

    /** Ở bước MUA thì ngược lại: mã sai phải chặn, không được âm thầm thu đủ giá. */
    @Test
    void purchase_invalidVoucher_isRejected() {
        when(voucherService.requireUsable("SAI"))
                .thenThrow(new BusinessException(com.fitmatch.common.enums.ErrorCode.INVALID_STATE,
                        "Voucher has expired"));
        TicketQuoteRequest req = request(false);
        req.setVoucherCode("SAI");

        assertThatThrownBy(() -> service.purchase(USERNAME, req))
                .isInstanceOf(BusinessException.class);
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void purchase_snapshotsPriceDayCountAndExpiry() {
        TicketPurchaseResponse response = service.purchase(USERNAME, request(true));

        assertThat(response.getTicket().getDayCount()).isEqualTo(10);
        assertThat(response.getTicket().getUnitPrice()).isEqualByComparingTo(BigDecimal.valueOf(1_000_000));
        assertThat(response.getTicket().getPtSurchargePerDay())
                .isEqualByComparingTo(BigDecimal.valueOf(200_000));
        // Vé gói: 90 ngày kể từ lúc mua, chốt tại đây chứ không đọc động về sau.
        assertThat(response.getTicket().getExpiresAt())
                .isAfter(LocalDateTime.now().plusDays(89))
                .isBefore(LocalDateTime.now().plusDays(91));
    }

    @Test
    void purchase_withoutPt_doesNotSnapshotSurcharge() {
        TicketPurchaseResponse response = service.purchase(USERNAME, request(false));

        assertThat(response.getTicket().getPtSurchargePerDay()).isNull();
        assertThat(response.getTicket().getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(1_000_000));
    }

    @Test
    void purchase_payableGreaterThanZero_createsPaymentOrder() {
        service.purchase(USERNAME, request(false));

        verify(paymentService).createOrderForTicket(any(Ticket.class));
        verify(ticketPaymentHandler, never()).onFullyDiscounted(any());
    }

    /** Câu 14: điểm phủ hết -> vé ACTIVE ngay, không tạo đơn QR. */
    @Test
    void purchase_fullyCoveredByPoints_activatesWithoutPaymentOrder() {
        when(loyaltyService.availablePoints(USERNAME)).thenReturn(5_000);
        TicketQuoteRequest req = request(false);
        req.setUseLoyaltyPoints(true);

        TicketPurchaseResponse response = service.purchase(USERNAME, req);

        assertThat(response.getPaymentOrder()).isNull();
        assertThat(response.getTicket().getPayableAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(ticketPaymentHandler).onFullyDiscounted(any(Ticket.class));
        verify(paymentService, never()).createOrderForTicket(any(Ticket.class));
    }

    /** Điểm và voucher phải bị tiêu NGAY lúc mua, trước khi tiền về. */
    @Test
    void purchase_consumesPromotionsUpFront() {
        service.purchase(USERNAME, request(false));

        verify(voucherService).consumeForTicket(any(Ticket.class));
        verify(loyaltyService).consumeForTicket(any(Ticket.class));
    }

    @Test
    void purchase_ticketTypeNotSoldAtBranch_isRejected() {
        when(ticketTypeRepository.existsByIdAndBranches_GymBranch_Id(TYPE_ID, BRANCH_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.purchase(USERNAME, request(false)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không được bán tại chi nhánh");
    }

    @Test
    void purchase_hiddenTicketType_isRejected() {
        packageType.setStatus(CatalogStatus.HIDDEN);

        assertThatThrownBy(() -> service.purchase(USERNAME, request(false)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không được mở bán");
    }

    @Test
    void purchase_startsInPendingPayment() {
        TicketPurchaseResponse response = service.purchase(USERNAME, request(false));

        assertThat(response.getTicket().getStatus()).isEqualTo(TicketStatus.PENDING_PAYMENT);
    }
}
