package com.fitmatch.dto.payment;

import com.fitmatch.common.enums.BookingStatus;
import com.fitmatch.common.enums.PaymentStatus;
import com.fitmatch.common.enums.PaymentTxnAnomaly;
import com.fitmatch.common.enums.PaymentTxnDirection;
import com.fitmatch.common.enums.ReconStatus;
import com.fitmatch.common.enums.WithdrawalStatus;
import com.fitmatch.entity.PaymentTransaction;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Giao dịch ngân hàng trong hàng đợi đối soát (UC-053/056). */
@Getter
@Builder
public class PaymentTransactionResponse {

    private Long id;

    /** Id giao dịch bên Casso — dùng để tra cứu ở sao kê ngân hàng. */
    private String externalId;

    /** Âm với giao dịch ghi nợ (chi trả lệnh rút). */
    private BigDecimal amount;
    private String refCode;
    private String rawDescription;

    /** V61: IN = khách trả tiền booking, OUT = nền tảng chi trả lệnh rút. */
    private PaymentTxnDirection direction;

    private ReconStatus reconStatus;
    private PaymentTxnAnomaly anomaly;

    /** V61: lệnh rút khớp được (chỉ với direction = OUT). */
    private Long withdrawalRequestId;
    private BigDecimal withdrawalAmount;
    private WithdrawalStatus withdrawalStatus;
    private String withdrawalOwnerName;

    /** Đơn thanh toán khớp được (null khi UNMATCHED). */
    private Long paymentOrderId;
    private BigDecimal orderAmount;
    private PaymentStatus orderStatus;

    private Long bookingId;
    private BookingStatus bookingStatus;
    private String customerUsername;

    /** Phần lệch so với đơn (dương = chuyển thừa, âm = chuyển thiếu); null khi không khớp đơn. */
    private BigDecimal amountDifference;

    private String resolutionNote;
    private String resolvedBy;
    private LocalDateTime resolvedAt;
    private LocalDateTime createdAt;

    public static PaymentTransactionResponse of(PaymentTransaction t) {
        PaymentTransactionResponseBuilder b = PaymentTransactionResponse.builder()
                .id(t.getId())
                .externalId(t.getExternalId())
                .amount(t.getAmount())
                .refCode(t.getRefCode())
                .rawDescription(t.getRawDescription())
                .direction(t.getDirection())
                .reconStatus(t.getReconStatus())
                .anomaly(t.getAnomaly())
                .resolutionNote(t.getResolutionNote())
                .resolvedBy(t.getResolvedBy())
                .resolvedAt(t.getResolvedAt())
                .createdAt(t.getCreatedAt());

        if (t.getWithdrawalRequest() != null) {
            var wr = t.getWithdrawalRequest();
            b.withdrawalRequestId(wr.getId())
                    .withdrawalAmount(wr.getAmount())
                    .withdrawalStatus(wr.getStatus())
                    .withdrawalOwnerName(wr.getAccountHolder());
            if (t.getAmount() != null && wr.getAmount() != null) {
                // Chiều chi: amount âm nên so sánh trên giá trị tuyệt đối, dương =
                // chi thừa so với số đã duyệt.
                b.amountDifference(t.getAmount().abs().subtract(wr.getAmount()));
            }
        }
        if (t.getPaymentOrder() != null) {
            var order = t.getPaymentOrder();
            b.paymentOrderId(order.getId())
                    .orderAmount(order.getAmount())
                    .orderStatus(order.getStatus());
            if (t.getAmount() != null && order.getAmount() != null) {
                b.amountDifference(t.getAmount().subtract(order.getAmount()));
            }
            var booking = order.getBooking();
            if (booking != null) {
                b.bookingId(booking.getId())
                        .bookingStatus(booking.getStatus());
                if (booking.getCustomer() != null) {
                    b.customerUsername(booking.getCustomer().getUsername());
                }
            }
        }
        return b.build();
    }
}
