package com.fitmatch.service.support;

import com.fitmatch.entity.Ticket;
import com.fitmatch.service.LoyaltyService;
import com.fitmatch.service.VoucherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Hoàn điểm thưởng + trả lại lượt voucher khi một vé kết thúc bằng huỷ hoặc
 * hoàn tiền. Bản cho mô hình vé của {@link BookingPromotionRefunder}, giữ
 * nguyên hai bất biến đã đúng ở mô hình cũ:
 *
 * <ul>
 *   <li><b>Chỉ hoàn khi đã thực tiêu:</b> điểm bị REDEEM và voucher usedCount
 *       tăng ngay tại lúc mua — TRƯỚC khi tiền về. Vé nào cũng đã đi qua bước
 *       đó nên ở đây không cần gate theo trạng thái trước như booking (mô hình
 *       vé không có DRAFT).</li>
 *   <li><b>Chỉ hoàn đúng một lần:</b> cờ {@code promoReleased} trên vé. State
 *       machine đã bảo đảm mỗi vé chỉ vào trạng thái kết thúc một lần, cờ là
 *       lớp phòng thủ thứ hai cho các đường huỷ chạy song song.</li>
 * </ul>
 *
 * <p>Vé EXPIRED cố ý KHÔNG đi qua đây: hết hạn là tiền về gym (câu 32), điểm
 * coi như đã tiêu — giống cách NO_SHOW không được hoàn ở mô hình cũ.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TicketPromotionReleaser {

    private final LoyaltyService loyaltyService;
    private final VoucherService voucherService;

    public void release(Ticket ticket) {
        if (ticket.isPromoReleased()) {
            return;
        }
        loyaltyService.refundToTicket(ticket);
        voucherService.releaseFromTicket(ticket);
        ticket.setPromoReleased(true);
        log.info("Promotions released for ticket {}", ticket.getId());
    }
}
