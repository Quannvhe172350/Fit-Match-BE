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
     * Giá gốc = baseAmount + ptSurchargeAmount + servicesAmount, chưa trừ giảm giá.
     */
    private BigDecimal totalAmount;

    /**
     * Tiền của riêng tấm vé (giá niêm yết, chưa phụ phí PT, chưa dịch vụ) — dòng
     * đầu của bảng kê. FE KHÔNG được tự suy ra bằng phép trừ: cứ chia lại tiền ở
     * client là có ngày lệch với số server thu.
     */
    private BigDecimal baseAmount;

    /**
     * Phụ phí PT của cả vé, ĐÃ nhân số ngày (câu 6: phụ phí tính theo từng ngày).
     * 0 khi khách không chọn PT hoặc loại vé không cấu hình phụ phí. Nằm trong
     * totalAmount, tách ra để khách thấy vì sao tổng cao hơn giá niêm yết.
     */
    private BigDecimal ptSurchargeAmount;

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
