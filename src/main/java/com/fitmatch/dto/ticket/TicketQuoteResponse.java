package com.fitmatch.dto.ticket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/** Bảng kê tiền hiển thị ở màn checkout trước khi khách bấm mua. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketQuoteResponse {

    private String ticketTypeName;
    private Integer dayCount;
    private boolean withPt;

    /**
     * Giá gốc = price + ptSurchargePerDay * dayCount (nếu chọn PT) + servicesAmount.
     */
    private BigDecimal totalAmount;

    /** V82: tổng tiền dịch vụ kèm theo, đã nằm trong totalAmount. */
    private BigDecimal servicesAmount;

    /** Từng dòng dịch vụ được chọn — FE dựng bảng kê, không tự tra giá lại. */
    private List<ServiceLine> services;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceLine {
        private Long id;
        private String name;
        private BigDecimal price;
    }

    private BigDecimal voucherDiscount;
    /** null khi không áp voucher, hoặc khi mã không đủ điều kiện (kèm voucherMessage). */
    private String voucherCode;
    /** Lý do voucher không áp được — FE hiện dưới ô nhập mã. */
    private String voucherMessage;

    /** Số điểm khách đang có, để FE hiện "Dùng 250 điểm (~250.000đ)". */
    private int loyaltyPointsAvailable;
    private int loyaltyPointsUsed;
    private BigDecimal loyaltyDiscount;

    /** Số tiền thực phải chuyển khoản. 0 = vé ACTIVE ngay, không hiện QR. */
    private BigDecimal payableAmount;
}
