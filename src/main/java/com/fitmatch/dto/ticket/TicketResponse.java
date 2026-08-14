package com.fitmatch.dto.ticket;

import com.fitmatch.common.enums.SettlementStatus;
import com.fitmatch.common.enums.TicketKind;
import com.fitmatch.common.enums.TicketStatus;
import com.fitmatch.entity.Ticket;
import com.fitmatch.entity.TrainingSession;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketResponse {

    private Long id;
    private Long customerId;
    private String customerName;

    private Long ticketTypeId;
    private String ticketTypeName;
    private Long gymProfileId;
    private String gymName;
    private Long gymBranchId;
    private String gymBranchName;

    private TicketKind kind;
    private Integer dayCount;
    private boolean withPt;

    private BigDecimal unitPrice;
    private BigDecimal ptSurchargePerDay;
    private BigDecimal totalAmount;
    private BigDecimal discountAmount;
    private Integer loyaltyPointsUsed;
    private BigDecimal payableAmount;

    /** V82: tổng tiền dịch vụ kèm vé, đã nằm trong totalAmount. */
    private BigDecimal servicesAmount;

    /** Dịch vụ đã mua kèm — tên/giá là snapshot lúc mua, không tra lại catalog. */
    private List<ServiceItem> services;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceItem {
        private Long id;
        private String name;
        private BigDecimal price;
    }

    private TicketStatus status;
    private String statusReason;
    private LocalDateTime purchasedAt;
    private LocalDateTime expiresAt;
    private LocalDate startDate;

    private SettlementStatus settlementStatus;

    /** Số ngày đã đặt lịch (không tính ngày đã huỷ) — FE hiện "3/10 ngày". */
    private Integer scheduledDays;

    /**
     * D-18: ngày cuối còn mở được tranh chấp; null = chưa tới hạn (vé chưa kết
     * toán) hoặc không áp hạn. Server tính (cần config runtime) rồi trả xuống để
     * FE không phải nhân bản luật — hằng số ở FE là cách chắc chắn nhất để hai
     * bên lệch nhau khi admin đổi cấu hình.
     */
    private LocalDate disputeDeadline;

    /** Chỉ có ở endpoint chi tiết; danh sách trang thì để null cho nhẹ. */
    private List<TrainingSessionResponse> sessions;

    public static TicketResponse of(Ticket t) {
        return base(t).build();
    }

    /**
     * Bản danh sách kèm số ngày ĐÃ xếp. Không có số này thì FE không phân biệt
     * được vé cần xếp lịch với vé đã xếp xong — cả hai đều hiện "0/N".
     */
    public static TicketResponse withScheduledCount(Ticket t, int scheduledDays) {
        return base(t).scheduledDays(scheduledDays).build();
    }

    /** Bản chi tiết kèm danh sách ngày tập. */
    public static TicketResponse withSessions(Ticket t, List<TrainingSession> sessions) {
        return base(t)
                .scheduledDays((int) sessions.stream()
                        .filter(s -> s.getStatus() != com.fitmatch.common.enums.SessionStatus.CANCELLED)
                        .count())
                .sessions(sessions.stream().map(TrainingSessionResponse::of).toList())
                .build();
    }

    private static TicketResponseBuilder base(Ticket t) {
        return TicketResponse.builder()
                .id(t.getId())
                .customerId(t.getCustomer().getId())
                .customerName(t.getCustomer().getFullName())
                .ticketTypeId(t.getTicketType().getId())
                .ticketTypeName(t.getTicketType().getName())
                .gymProfileId(t.getGymProfile().getId())
                .gymName(t.getGymProfile().getGymName())
                .gymBranchId(t.getGymBranch().getId())
                .gymBranchName(t.getGymBranch().getName())
                .kind(t.getKind())
                .dayCount(t.getDayCount())
                .withPt(t.isWithPt())
                .unitPrice(t.getUnitPrice())
                .ptSurchargePerDay(t.getPtSurchargePerDay())
                .totalAmount(t.getTotalAmount())
                .servicesAmount(t.getServicesAmount())
                .services(t.getServiceItems().stream()
                        .map(i -> ServiceItem.builder()
                                .id(i.getId()).name(i.getName()).price(i.getPrice()).build())
                        .toList())
                .discountAmount(t.getDiscountAmount())
                .loyaltyPointsUsed(t.getLoyaltyPointsUsed())
                .payableAmount(t.getPayableAmount())
                .status(t.getStatus())
                .statusReason(t.getStatusReason())
                .purchasedAt(t.getPurchasedAt())
                .expiresAt(t.getExpiresAt())
                .startDate(t.getStartDate())
                .settlementStatus(t.getSettlementStatus());
    }
}
