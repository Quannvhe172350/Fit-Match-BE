package com.fitmatch.dto.ticket;

import com.fitmatch.dto.payment.PaymentOrderResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketPurchaseResponse {

    private TicketResponse ticket;

    /**
     * null khi payableAmount == 0 (điểm thưởng/voucher phủ hết — câu 14): vé
     * ACTIVE ngay và FE không hiện QR.
     */
    private PaymentOrderResponse paymentOrder;
}
