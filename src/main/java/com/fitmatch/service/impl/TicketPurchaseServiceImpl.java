package com.fitmatch.service.impl;

import com.fitmatch.common.enums.CatalogStatus;
import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.SessionStatus;
import com.fitmatch.common.enums.TicketKind;
import com.fitmatch.common.enums.TicketStatus;
import com.fitmatch.common.enums.VerificationStatus;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.payment.PaymentOrderResponse;
import com.fitmatch.dto.ticket.TicketPurchaseResponse;
import com.fitmatch.dto.ticket.TicketQuoteRequest;
import com.fitmatch.dto.ticket.TicketQuoteResponse;
import com.fitmatch.dto.ticket.TicketResponse;
import com.fitmatch.dto.ticket.TicketStatusHistoryResponse;
import com.fitmatch.entity.GymBranch;
import com.fitmatch.entity.GymService;
import com.fitmatch.entity.PlatformTicketConfig;
import com.fitmatch.entity.Ticket;
import com.fitmatch.entity.TicketServiceItem;
import com.fitmatch.entity.TicketType;
import com.fitmatch.entity.User;
import com.fitmatch.entity.Voucher;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.GymBranchRepository;
import com.fitmatch.repository.GymServiceRepository;
import com.fitmatch.repository.PlatformTicketConfigRepository;
import com.fitmatch.repository.TicketRepository;
import com.fitmatch.repository.TicketStatusHistoryRepository;
import com.fitmatch.repository.TicketTypeRepository;
import com.fitmatch.repository.TrainingSessionRepository;
import com.fitmatch.repository.UserRepository;
import com.fitmatch.service.LoyaltyService;
import com.fitmatch.service.PaymentService;
import com.fitmatch.service.TicketPurchaseService;
import com.fitmatch.service.VoucherService;
import com.fitmatch.service.support.TicketLifecycle;
import com.fitmatch.service.support.TicketPaymentHandler;
import com.fitmatch.service.support.TicketPriceCalculator;
import com.fitmatch.service.support.TicketPriceCalculator.TicketPricing;
import com.fitmatch.service.support.TicketPromotionReleaser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketPurchaseServiceImpl implements TicketPurchaseService {

    private final TicketRepository ticketRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final TicketStatusHistoryRepository ticketStatusHistoryRepository;
    private final TrainingSessionRepository trainingSessionRepository;
    private final GymBranchRepository gymBranchRepository;
    private final GymServiceRepository gymServiceRepository;
    private final UserRepository userRepository;
    private final TicketPriceCalculator priceCalculator;
    private final PlatformTicketConfigRepository ticketConfigRepository;
    private final VoucherService voucherService;
    private final LoyaltyService loyaltyService;
    private final PaymentService paymentService;
    private final TicketLifecycle ticketLifecycle;
    private final TicketPaymentHandler ticketPaymentHandler;
    private final TicketPromotionReleaser ticketPromotionReleaser;
    private final com.fitmatch.service.support.DisputeWindow disputeWindow;

    @Override
    @Transactional(readOnly = true)
    public TicketQuoteResponse quote(String customerUsername, TicketQuoteRequest request) {
        TicketType type = requireOnSale(request.getTicketTypeId(), request.getBranchId());

        // Voucher không hợp lệ ở màn báo giá KHÔNG được ném lỗi: khách gõ sai mã
        // thì vẫn phải thấy giá vé, chỉ kèm lời giải thích dưới ô nhập.
        //
        // Phải hỏi bằng checkUsable chứ KHÔNG bắt BusinessException của
        // requireUsable: requireUsable cũng @Transactional nên ngoại lệ ném ra từ
        // đó đánh dấu transaction dùng chung là rollback-only, bắt xong chạy tiếp
        // vẫn vỡ UnexpectedRollbackException lúc commit → 500 cho mọi mã sai.
        Voucher voucher = null;
        String voucherMessage = null;
        if (hasText(request.getVoucherCode())) {
            VoucherService.UsableCheck check = voucherService.checkUsable(request.getVoucherCode());
            voucher = check.voucher();
            voucherMessage = check.message();
        }

        int balance = loyaltyService.availablePoints(customerUsername);
        int availablePoints = request.isUseLoyaltyPoints() ? balance : 0;

        List<GymService> services = resolveServices(type, request.getServiceIds());
        TicketPricing pricing = price(type, request.isWithPt(), voucher, availablePoints, services);
        if (voucher != null && pricing.voucherDiscount().compareTo(BigDecimal.ZERO) <= 0) {
            voucherMessage = "Mã không áp dụng được cho vé này (chưa đạt giá trị tối thiểu)";
        }

        return TicketQuoteResponse.builder()
                .ticketTypeName(type.getName())
                .dayCount(type.getDayCount())
                .withPt(request.isWithPt())
                .totalAmount(pricing.totalAmount())
                .servicesAmount(pricing.servicesAmount())
                .services(services.stream()
                        .map(s -> TicketQuoteResponse.ServiceLine.builder()
                                .id(s.getId()).name(s.getName()).price(s.getPrice()).build())
                        .toList())
                .voucherDiscount(pricing.voucherDiscount())
                .voucherCode(voucher != null ? voucher.getCode() : null)
                .voucherMessage(voucherMessage)
                .loyaltyPointsAvailable(balance)
                .loyaltyPointsUsed(pricing.loyaltyPointsUsed())
                .loyaltyDiscount(pricing.loyaltyDiscount())
                .payableAmount(pricing.payableAmount())
                .build();
    }

    @Override
    @Transactional
    public TicketPurchaseResponse purchase(String customerUsername, TicketQuoteRequest request) {
        User customer = userRepository.findByUsername(customerUsername)
                .orElseThrow(() -> new ResourceNotFoundException("User", customerUsername));
        TicketType type = requireOnSale(request.getTicketTypeId(), request.getBranchId());
        GymBranch branch = gymBranchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Gym branch", request.getBranchId()));
        assertGymOpen(type);

        // Ở đây voucher sai thì PHẢI ném lỗi (khác /quote): khách đã bấm mua nên
        // im lặng bỏ mã đồng nghĩa với thu nhiều hơn số họ vừa nhìn thấy.
        Voucher voucher = hasText(request.getVoucherCode())
                ? voucherService.requireUsable(request.getVoucherCode()) : null;
        int availablePoints = request.isUseLoyaltyPoints()
                ? loyaltyService.availablePoints(customerUsername) : 0;

        List<GymService> services = resolveServices(type, request.getServiceIds());
        TicketPricing pricing = price(type, request.isWithPt(), voucher, availablePoints, services);

        Ticket ticket = Ticket.builder()
                .customer(customer)
                .ticketType(type)
                .gymProfile(type.getGymProfile())
                .gymBranch(branch)
                // Snapshot toàn bộ: gym đổi bảng giá hay admin đổi hạn vé về sau
                // đều không làm sai vé này.
                .kind(type.getKind())
                .dayCount(type.getDayCount())
                .withPt(request.isWithPt())
                .unitPrice(type.getPrice())
                .ptSurchargePerDay(request.isWithPt() ? type.getPtSurchargePerDay() : null)
                .voucher(voucher)
                .status(TicketStatus.PENDING_PAYMENT)
                .expiresAt(expiryOf(type.getKind()))
                .build();
        priceCalculator.applyTo(ticket, pricing);
        // Snapshot tên + giá dịch vụ vào vé: gym sửa bảng giá ngày mai không được
        // làm đổi số tiền của vé đã bán (cùng nguyên tắc với unitPrice).
        for (GymService service : services) {
            ticket.getServiceItems().add(TicketServiceItem.builder()
                    .ticket(ticket)
                    .gymService(service)
                    .name(service.getName())
                    .price(service.getPrice())
                    .build());
        }
        ticket = ticketRepository.save(ticket);

        // Tiêu điểm/voucher NGAY tại lúc mua, trước khi tiền về — giữ nguyên cách
        // làm của mô hình cũ. Vé huỷ vì quá hạn thanh toán sẽ được hoàn lại qua
        // TicketPromotionReleaser.
        voucherService.consumeForTicket(ticket);
        loyaltyService.consumeForTicket(ticket);

        if (ticket.getPayableAmount().compareTo(BigDecimal.ZERO) <= 0) {
            ticketPaymentHandler.onFullyDiscounted(ticket);
            return TicketPurchaseResponse.builder()
                    .ticket(TicketResponse.of(ticket))
                    .build();
        }

        PaymentOrderResponse order = paymentService.createOrderForTicket(ticket);
        log.info("Customer {} purchased ticket {} ({}), payable {}",
                customerUsername, ticket.getId(), type.getName(), ticket.getPayableAmount());
        return TicketPurchaseResponse.builder()
                .ticket(TicketResponse.of(ticket))
                .paymentOrder(order)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TicketResponse> myTickets(String customerUsername, TicketStatus status,
                                                  Pageable pageable) {
        var page = status != null
                ? ticketRepository.findByCustomer_UsernameAndStatus(customerUsername, status, pageable)
                : ticketRepository.findByCustomer_Username(customerUsername, pageable);

        // Đếm ngày đã xếp cho CẢ trang bằng một truy vấn — màn "Vé của tôi" dùng
        // số này để chọn nút ("Xếp lịch" hay "Xem lịch"), và trang đặt lịch dùng
        // nó để loại vé đã xếp xong khỏi danh sách chọn.
        Map<Long, Integer> scheduled = scheduledDaysOf(
                page.getContent().stream().map(Ticket::getId).toList());

        return PageResponse.of(page, ticket -> withDisputeDeadline(
                TicketResponse.withScheduledCount(ticket, scheduled.getOrDefault(ticket.getId(), 0)),
                ticket));
    }

    private Map<Long, Integer> scheduledDaysOf(List<Long> ticketIds) {
        if (ticketIds.isEmpty()) {
            return Map.of();
        }
        return trainingSessionRepository
                .countScheduledByTicketIds(ticketIds, SessionStatus.CANCELLED)
                .stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> ((Number) row[1]).intValue()));
    }

    @Override
    @Transactional(readOnly = true)
    public TicketResponse detail(String customerUsername, Long ticketId) {
        Ticket ticket = requireOwned(customerUsername, ticketId);
        return withDisputeDeadline(TicketResponse.withSessions(ticket,
                trainingSessionRepository.findByTicket_IdOrderByDayIndexAsc(ticketId)), ticket);
    }

    /** D-18: hạn mở tranh chấp — chỉ gắn ở API của khách, đây là bên cần biết. */
    private TicketResponse withDisputeDeadline(TicketResponse response, Ticket ticket) {
        response.setDisputeDeadline(disputeWindow.deadline(ticket));
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentOrderResponse payment(String customerUsername, Long ticketId) {
        return paymentService.getForTicketCustomer(ticketId, customerUsername);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TicketStatusHistoryResponse> history(String customerUsername, Long ticketId) {
        requireOwned(customerUsername, ticketId);
        return ticketStatusHistoryRepository.findByTicket_IdOrderByCreatedAtAsc(ticketId)
                .stream().map(TicketStatusHistoryResponse::of).toList();
    }

    @Override
    @Transactional
    public TicketResponse cancelUnpaid(String customerUsername, Long ticketId) {
        Ticket ticket = requireOwned(customerUsername, ticketId);
        // Chỉ vé chưa trả tiền mới huỷ thẳng được; vé đã ACTIVE phải đi đường
        // yêu cầu hoàn tiền để admin quyết mức hoàn (câu 11).
        if (ticket.getStatus() != TicketStatus.PENDING_PAYMENT) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Chỉ huỷ được vé chưa thanh toán (hiện: " + ticket.getStatus() + ")");
        }
        ticketLifecycle.transition(ticket, TicketStatus.CANCELLED, "Cancelled by customer before payment");
        paymentService.cancelTicketOrderIfPending(ticketId);
        ticketPromotionReleaser.release(ticket);
        return TicketResponse.of(ticket);
    }

    // ---------- helpers ----------

    private TicketPricing price(TicketType type, boolean withPt, Voucher voucher,
                                int availablePoints, List<GymService> services) {
        BigDecimal servicesAmount = services.stream()
                .map(GymService::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal voucherDiscount = BigDecimal.ZERO;
        if (voucher != null) {
            BigDecimal surcharge = type.getPtSurchargePerDay() != null
                    ? type.getPtSurchargePerDay() : BigDecimal.ZERO;
            BigDecimal total = type.getPrice();
            if (withPt) {
                total = total.add(surcharge.multiply(BigDecimal.valueOf(type.getDayCount())));
            }
            // Ngưỡng tối thiểu của voucher xét trên tổng ĐÃ gồm dịch vụ, khớp với
            // total mà TicketPriceCalculator dùng — hai chỗ lệch nhau thì khách
            // thấy một mức giảm ở /quote và bị trừ mức khác lúc mua.
            voucherDiscount = voucherService.computeDiscount(voucher, total.add(servicesAmount));
        }
        return priceCalculator.calculate(type.getPrice(), type.getPtSurchargePerDay(),
                type.getDayCount(), withPt, servicesAmount, voucherDiscount, availablePoints);
    }

    /**
     * Dịch vụ phải thuộc ĐÚNG gym của vé và còn mở bán. Không kiểm thì khách gửi
     * id dịch vụ của gym khác vào là mua được combo giá rẻ của nơi khác.
     */
    private List<GymService> resolveServices(TicketType type, List<Long> serviceIds) {
        if (serviceIds == null || serviceIds.isEmpty()) {
            return List.of();
        }
        return new LinkedHashSet<>(serviceIds).stream()
                .map(id -> {
                    GymService service = gymServiceRepository.findById(id)
                            .orElseThrow(() -> new ResourceNotFoundException("Gym service", id));
                    if (!service.getGymProfile().getId().equals(type.getGymProfile().getId())) {
                        throw new BusinessException(ErrorCode.FORBIDDEN,
                                "Dịch vụ " + id + " không thuộc phòng gym của vé này");
                    }
                    if (service.getStatus() != CatalogStatus.PUBLISHED || !service.isActive()) {
                        throw new BusinessException(ErrorCode.INVALID_STATE,
                                "Dịch vụ '" + service.getName() + "' hiện không được mở bán");
                    }
                    return service;
                })
                .toList();
    }

    /** Câu 32: hạn vé chốt tại thời điểm mua từ config admin — không đọc động. */
    private LocalDateTime expiryOf(TicketKind kind) {
        PlatformTicketConfig config = ticketConfigRepository.findTopByOrderByIdDesc()
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR,
                        "Platform ticket config is missing (V67 seed)"));
        int days = kind == TicketKind.DAY
                ? config.getDayTicketExpiryDays() : config.getPackageTicketExpiryDays();
        return LocalDateTime.now().plusDays(days);
    }

    private TicketType requireOnSale(Long ticketTypeId, Long branchId) {
        TicketType type = ticketTypeRepository.findById(ticketTypeId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket type", ticketTypeId));
        if (type.getStatus() != CatalogStatus.PUBLISHED || !type.isActive()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Loại vé này hiện không được mở bán");
        }
        if (!ticketTypeRepository.existsByIdAndBranches_GymBranch_Id(ticketTypeId, branchId)) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Loại vé này không được bán tại chi nhánh đã chọn");
        }
        return type;
    }

    private void assertGymOpen(TicketType type) {
        if (type.getGymProfile().getVerificationStatus() != VerificationStatus.APPROVED
                || !type.getGymProfile().isActive()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Phòng gym hiện không bán vé");
        }
    }

    private Ticket requireOwned(String customerUsername, Long ticketId) {
        return ticketRepository.findByIdAndCustomer_Username(ticketId, customerUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
