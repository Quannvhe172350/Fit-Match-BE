package com.fitmatch.service.support;

import com.fitmatch.common.enums.BookingStatus;
import com.fitmatch.entity.Booking;
import com.fitmatch.service.LoyaltyService;
import com.fitmatch.service.VoucherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Hoàn điểm thưởng + trả lại lượt voucher khi một booking đã checkout nhưng kết
 * thúc bằng hủy/từ chối (UC-073, P1 batch 2).
 *
 * <p>Điểm bị REDEEM và voucher usedCount bị tăng ngay tại checkout — TRƯỚC khi
 * tiền về. Nếu booking sau đó bị hủy/từ chối/hết hạn thanh toán mà không hoàn
 * điểm/voucher, khách mất giá trị thật (1 điểm = 1.000đ) dù chưa dùng dịch vụ.
 *
 * <p>Gộp logic ở một chỗ để dùng chung cho mọi đường hủy (customer cancel, gym
 * reject, gym cancel, payment expiry). Bảo đảm hai bất biến:
 * <ul>
 *   <li><b>Chỉ hoàn khi đã thực tiêu:</b> gate theo trạng thái TRƯỚC khi hủy —
 *       booking còn DRAFT chưa qua checkout nên chưa REDEEM/chưa tăng usedCount,
 *       không được hoàn (nếu không sẽ tạo điểm từ hư không).</li>
 *   <li><b>Chỉ hoàn đúng một lần:</b> cờ {@code promoReleased} trên booking
 *       (state machine cũng bảo đảm mỗi booking chỉ chuyển sang trạng thái hủy
 *       một lần, nhưng cờ là lớp phòng thủ thứ hai).</li>
 * </ul>
 * NO_SHOW cố ý KHÔNG hoàn: khách vắng mặt, tiền settle cho gym, điểm coi như đã tiêu.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BookingPromotionRefunder {

    private final LoyaltyService loyaltyService;
    private final VoucherService voucherService;

    /**
     * @param booking    booking vừa chuyển sang CANCELLED/REJECTED
     * @param statusBeforeCancel trạng thái ngay trước khi hủy (để biết đã checkout chưa)
     */
    public void releaseOnCancellation(Booking booking, BookingStatus statusBeforeCancel) {
        if (statusBeforeCancel == BookingStatus.DRAFT) {
            return; // chưa checkout -> chưa tiêu điểm/voucher
        }
        if (booking.isPromoReleased()) {
            return; // đã hoàn rồi
        }
        loyaltyService.refundToBooking(booking);
        voucherService.releaseFromBooking(booking);
        booking.setPromoReleased(true);
    }
}
