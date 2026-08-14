package com.fitmatch.service;

import com.fitmatch.common.enums.TicketStatus;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.payment.PaymentOrderResponse;
import com.fitmatch.dto.ticket.TicketPurchaseResponse;
import com.fitmatch.dto.ticket.TicketQuoteRequest;
import com.fitmatch.dto.ticket.TicketQuoteResponse;
import com.fitmatch.dto.ticket.TicketResponse;
import com.fitmatch.dto.ticket.TicketStatusHistoryResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

/** Mua vé: báo giá, chốt đơn, tra cứu vé của khách. */
public interface TicketPurchaseService {

    /**
     * Báo giá trước khi mua — chỉ đọc, không tạo gì. Nhận cùng input với
     * {@link #purchase} để con số hiển thị và con số bị trừ không bao giờ lệch.
     */
    TicketQuoteResponse quote(String customerUsername, TicketQuoteRequest request);

    /**
     * Chốt mua. payableAmount == 0 (điểm/voucher phủ hết) thì vé ACTIVE ngay và
     * paymentOrder = null; ngược lại vé PENDING_PAYMENT kèm đơn VietQR.
     */
    TicketPurchaseResponse purchase(String customerUsername, TicketQuoteRequest request);

    PageResponse<TicketResponse> myTickets(String customerUsername, TicketStatus status, Pageable pageable);

    /** Chi tiết vé kèm danh sách ngày tập. */
    TicketResponse detail(String customerUsername, Long ticketId);

    PaymentOrderResponse payment(String customerUsername, Long ticketId);

    List<TicketStatusHistoryResponse> history(String customerUsername, Long ticketId);

    /** Khách tự huỷ vé chưa thanh toán — đóng đơn QR và hoàn điểm/voucher. */
    TicketResponse cancelUnpaid(String customerUsername, Long ticketId);
}
