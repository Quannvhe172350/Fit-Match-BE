package com.fitmatch.service.support;

import com.fitmatch.entity.GymPolicy;
import com.fitmatch.entity.Ticket;
import com.fitmatch.entity.TrainingSession;
import com.fitmatch.repository.GymPolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

/**
 * V94: khách huỷ MỘT ngày tập — hoàn bao nhiêu.
 *
 * <p>Hai câu hỏi tách rời nhau, cố ý:
 *
 * <ol>
 *   <li><b>Một ngày của vé đáng bao nhiêu tiền?</b> Vé bán trọn gói nên phải quy
 *       về từng ngày: {@code payableAmount / dayCount}. Dùng {@code payableAmount}
 *       (số khách THỰC TRẢ) chứ không phải {@code totalAmount} (giá niêm yết) —
 *       voucher và điểm thưởng đã giảm giá toàn vé, hoàn theo giá niêm yết là trả
 *       cho khách nhiều hơn số họ bỏ ra. Cùng nguyên tắc với
 *       {@link PtDayRefundCalculator}.</li>
 *   <li><b>Báo trước sớm thì được bao nhiêu phần trăm?</b> Do chính sách của gym
 *       quyết ({@code gym_policies}), không phải hằng số trong mã.</li>
 * </ol>
 *
 * <p>Mốc thời gian tính tới ĐẦU BUỔI, không phải đầu ngày: buổi có HLV lúc 19:00
 * mà tính từ 00:00 thì khách huỷ lúc 14:00 cùng ngày bị coi là "đã trễ 14 tiếng"
 * trong khi thực ra vẫn còn báo trước 5 tiếng. Buổi không có khung giờ (tự tập)
 * thì mốc là 00:00 vì vé dùng được từ lúc gym mở cửa.
 *
 * <p>Trần an toàn cuối cùng: không bao giờ hoàn quá phần vé còn hoàn được
 * ({@code payableAmount} trừ những gì đã hoàn lẻ). Bất biến này phải nằm ở đây
 * vì {@code WalletServiceImpl} chỉ kiểm {@code held_balance} TỔNG của gym, không
 * biết gì về từng vé.
 */
@Component
@RequiredArgsConstructor
public class SessionCancelRefundCalculator {

    /** Dùng khi gym chưa cấu hình chính sách — xem V94 về lý do có mặc định. */
    private static final int DEFAULT_FULL_HOURS = 24;
    private static final int DEFAULT_PARTIAL_HOURS = 12;
    private static final BigDecimal DEFAULT_PARTIAL_PERCENT = new BigDecimal("50.00");

    private final GymPolicyRepository gymPolicyRepository;

    /**
     * @param hoursAhead số giờ báo trước (âm = buổi đã bắt đầu)
     * @param percent    tỉ lệ hoàn được áp dụng, 0..100
     * @param amount     số tiền thực hoàn về ví khách
     * @param perDayValue giá trị một ngày tập của vé — để FE giải thích con số
     */
    public record Quote(long hoursAhead, BigDecimal percent, BigDecimal amount,
                        BigDecimal perDayValue) {
    }

    /** Ba mốc đang áp dụng cho một gym — hiện trong hộp xác nhận để khách hiểu luật. */
    public record Tiers(int fullHours, int partialHours, BigDecimal partialPercent) {
    }

    public Quote quote(TrainingSession session) {
        return quote(session, LocalDateTime.now());
    }

    /** Bản nhận mốc thời gian tường minh — test không phụ thuộc đồng hồ máy. */
    public Quote quote(TrainingSession session, LocalDateTime now) {
        Ticket ticket = session.getTicket();
        LocalDateTime startsAt = session.getSessionDate()
                .atTime(session.getPtSlotStart() != null ? session.getPtSlotStart() : LocalTime.MIDNIGHT);
        long hoursAhead = ChronoUnit.HOURS.between(now, startsAt);

        BigDecimal percent = percentFor(tiersFor(ticket), hoursAhead);
        BigDecimal perDay = perDayValue(ticket);
        BigDecimal refund = perDay.multiply(percent)
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);

        return new Quote(hoursAhead, percent, refund.min(remainingRefundable(ticket)), perDay);
    }

    /**
     * Giá trị một ngày tập theo số khách THỰC TRẢ. Vé một ngày thì đúng bằng cả
     * vé; vé gói thì chia đều — cùng cách chia mà
     * {@link PartialRefundCalculator} dùng khi admin hoàn vé trừ ngày đã qua, nên
     * hai đường hoàn không cho ra hai định nghĩa "một ngày" khác nhau.
     */
    private BigDecimal perDayValue(Ticket ticket) {
        BigDecimal payable = ticket.getPayableAmount();
        Integer dayCount = ticket.getDayCount();
        if (payable == null || payable.signum() <= 0 || dayCount == null || dayCount <= 0) {
            return BigDecimal.ZERO;
        }
        return payable.divide(BigDecimal.valueOf(dayCount), 0, RoundingMode.HALF_UP);
    }

    /**
     * Phần còn hoàn được của vé: đã trả trừ mọi khoản hoàn lẻ trước đó (phụ phí
     * HLV vì PT nghỉ, và những ngày đã huỷ trước đó).
     */
    private BigDecimal remainingRefundable(Ticket ticket) {
        BigDecimal payable = ticket.getPayableAmount();
        if (payable == null) {
            return BigDecimal.ZERO;
        }
        return payable
                .subtract(nvl(ticket.getPtRefundedAmount()))
                .subtract(nvl(ticket.getSessionRefundedAmount()))
                .max(BigDecimal.ZERO);
    }

    /**
     * Mốc đang áp dụng cho gym của vé. Gym chưa cấu hình thì dùng mặc định —
     * không có chính sách KHÔNG có nghĩa là không hoàn đồng nào, đó sẽ là một
     * luật khắc nghiệt mà không ai cố ý đặt ra.
     */
    public Tiers tiersFor(Ticket ticket) {
        GymPolicy policy = gymPolicyRepository
                .findByGymProfile_Id(ticket.getGymProfile().getId())
                .orElse(null);
        if (policy == null) {
            return new Tiers(DEFAULT_FULL_HOURS, DEFAULT_PARTIAL_HOURS, DEFAULT_PARTIAL_PERCENT);
        }
        return new Tiers(
                policy.getCancelFullRefundHours() != null
                        ? policy.getCancelFullRefundHours() : DEFAULT_FULL_HOURS,
                policy.getCancelPartialRefundHours() != null
                        ? policy.getCancelPartialRefundHours() : DEFAULT_PARTIAL_HOURS,
                policy.getCancelPartialRefundPercent() != null
                        ? policy.getCancelPartialRefundPercent() : DEFAULT_PARTIAL_PERCENT);
    }

    private BigDecimal percentFor(Tiers tiers, long hoursAhead) {
        if (hoursAhead >= tiers.fullHours()) {
            return new BigDecimal("100.00");
        }
        if (hoursAhead >= tiers.partialHours()) {
            return tiers.partialPercent();
        }
        return BigDecimal.ZERO;
    }

    private static BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
