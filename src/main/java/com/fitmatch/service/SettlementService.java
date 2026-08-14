package com.fitmatch.service;

import com.fitmatch.entity.Ticket;

import java.math.BigDecimal;
import java.util.List;

/**
 * Điều phối dòng tiền escrow theo vòng đời VÉ (UC-057..059). Trạng thái tiền
 * lưu ở {@code Ticket.settlementStatus}; bút toán do WalletService ghi.
 *
 * <p>Câu 15 + 32: mốc giải ngân là "vé USED_UP hoặc EXPIRED", không còn là
 * "hoàn tất một buổi tập".
 */
public interface SettlementService {

    /** Tiền vé vừa được giữ vào ví Gym — đánh dấu HELD. */
    void markTicketHeld(Ticket ticket);

    /** Chuyển toàn bộ phần held của vé sang pending settlement. Idempotent. */
    void settleTicketAfterFulfillment(Ticket ticket, String reason);

    /** id các vé PENDING_RELEASE đã hết holding period. */
    List<Long> findTicketsDueForRelease();

    /** Giải ngân một vé đến hạn — pending -> available, trừ hoa hồng đã chốt. */
    void releaseTicket(Long ticketId);

    /** Số tiền thực đang giữ cho vé (đơn PAID; fallback payableAmount). */
    BigDecimal heldAmountOfTicket(Ticket ticket);
}
