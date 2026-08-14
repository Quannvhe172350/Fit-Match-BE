package com.fitmatch.service.support;

import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.SessionStatus;
import com.fitmatch.common.enums.TicketKind;
import com.fitmatch.common.enums.TicketStatus;
import com.fitmatch.common.enums.VerificationStatus;
import com.fitmatch.entity.OperatingHour;
import com.fitmatch.entity.Ticket;
import com.fitmatch.entity.TrainingSession;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.repository.OperatingHourRepository;
import com.fitmatch.repository.TrainingSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Điều kiện đặt lịch ở cấp VÉ — nửa thứ nhất của {@code BookingEligibilityChecker}
 * cũ. Cố ý không biết gì về PT: buổi tập không có PT chỉ đi qua lớp này và
 * không bao giờ chạm {@link PtSlotValidator}.
 *
 * <p>Những thứ đã biến mất so với mô hình cũ: sức chứa, thời gian báo trước tối
 * thiểu, và ràng buộc "một khách một khung giờ". Vé có giá trị cả ngày nên trùng
 * ngày là chuyện bình thường — khách chỉ được CẢNH BÁO ở tầng API (câu 29),
 * không bị chặn.
 *
 * <p>Giờ mở cửa chi nhánh quay lại nhưng CHỈ cho đúng một câu hỏi: đặt lịch cho
 * CHÍNH HÔM NAY khi chi nhánh đã đóng cửa. Đặt cho ngày mai trở đi không cần
 * biết giờ giấc — gym còn cả ngày để mở cửa. Không tái lập ràng buộc "buổi tập
 * phải nằm trong giờ mở cửa": vé tính theo NGÀY, không theo khung giờ.
 *
 * <p>Hai mốc thời gian: 00:00 của ngày tập (ngày đã tới thì không dời/huỷ được)
 * và giờ đóng cửa của hôm nay (đặt cho hôm nay).
 */
@Component
@RequiredArgsConstructor
public class SessionSchedulingValidator {

    /** Buổi còn chiếm một ngày của vé; buổi CANCELLED trả lại ngày đó. */
    public static final Set<SessionStatus> CONSUMING_STATUSES =
            Set.of(SessionStatus.SCHEDULED, SessionStatus.DONE);

    private final TrainingSessionRepository trainingSessionRepository;
    private final OperatingHourRepository operatingHourRepository;

    /**
     * Kiểm tra vé còn đặt lịch được và các ngày yêu cầu hợp lệ.
     *
     * @param dates các ngày định đặt trong một lần gọi (vé DAY: 1 ngày; vé
     *              PACKAGE: đủ {@code dayCount} ngày liên tiếp)
     */
    public void assertSchedulable(Ticket ticket, List<LocalDate> dates) {
        assertSchedulable(ticket, dates, LocalDateTime.now());
    }

    /**
     * Bản nhận ngày tường minh — giữ cho các test cũ không phụ thuộc đồng hồ máy.
     * Quy về ĐẦU ngày: chỉ hỏi "ngày nào", nên mọi giờ đóng cửa đều còn ở phía
     * trước và luật giờ đóng cửa không đổi kết quả.
     */
    public void assertSchedulable(Ticket ticket, List<LocalDate> dates, LocalDate today) {
        assertSchedulable(ticket, dates, today.atStartOfDay());
    }

    /** Bản đầy đủ: cần cả GIỜ vì đặt cho hôm nay phải trước giờ đóng cửa. */
    public void assertSchedulable(Ticket ticket, List<LocalDate> dates, LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        List<String> reasons = new ArrayList<>(ticketIssues(ticket, today));

        if (dates == null || dates.isEmpty()) {
            reasons.add("Vui lòng chọn ít nhất một ngày tập");
        } else {
            if (new HashSet<>(dates).size() != dates.size()) {
                reasons.add("Danh sách ngày tập bị trùng");
            }
            for (LocalDate date : dates) {
                if (date.isBefore(today)) {
                    reasons.add("Không thể đặt lịch cho ngày đã qua: " + date);
                }
                if (ticket.getExpiresAt() != null && date.isAfter(ticket.getExpiresAt().toLocalDate())) {
                    reasons.add("Ngày " + date + " vượt quá hạn dùng của vé ("
                            + ticket.getExpiresAt().toLocalDate() + ")");
                }
                closedTodayIssue(ticket, date, now).ifPresent(reasons::add);
            }

            long alreadyBooked = trainingSessionRepository
                    .countByTicket_IdAndStatusNot(ticket.getId(), SessionStatus.CANCELLED);
            if (alreadyBooked + dates.size() > ticket.getDayCount()) {
                reasons.add("Vé chỉ còn " + Math.max(0, ticket.getDayCount() - alreadyBooked)
                        + " ngày chưa đặt (yêu cầu " + dates.size() + " ngày)");
            }
            // Vé gói phải đặt trọn gói một lần: n ngày liên tiếp từ ngày bắt đầu.
            // Đặt lẻ từng ngày sẽ phá luôn ý nghĩa của "gói liên tiếp" ở câu 27.
            if (ticket.getKind() == TicketKind.PACKAGE
                    && (alreadyBooked > 0 || dates.size() != ticket.getDayCount())) {
                reasons.add("Vé gói phải đặt trọn " + ticket.getDayCount() + " ngày trong một lần");
            }
        }

        reject(reasons);
    }

    /**
     * Câu 2/37: chỉ vé DAY được dời ngày. Vé PACKAGE gọi vào đây là 409 —
     * n ngày liên tiếp không còn liên tiếp nếu rút một ngày ra giữa.
     */
    public void assertReschedulable(Ticket ticket, TrainingSession session, LocalDate newDate) {
        assertReschedulable(ticket, session, newDate, LocalDateTime.now());
    }

    /** Xem {@link #assertSchedulable(Ticket, List, LocalDate)} về việc quy đầu ngày. */
    public void assertReschedulable(Ticket ticket, TrainingSession session,
                                    LocalDate newDate, LocalDate today) {
        assertReschedulable(ticket, session, newDate, today.atStartOfDay());
    }

    /** Dời sang HÔM NAY cũng phải trước giờ đóng cửa — cùng luật với đặt mới. */
    public void assertReschedulable(Ticket ticket, TrainingSession session,
                                    LocalDate newDate, LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        List<String> reasons = new ArrayList<>(ticketIssues(ticket, today));

        if (ticket.getKind() == TicketKind.PACKAGE) {
            reasons.add("Vé gói không đổi được lịch từng ngày");
        }
        if (session.getStatus() != SessionStatus.SCHEDULED) {
            reasons.add("Buổi tập không còn ở trạng thái đặt trước (hiện: " + session.getStatus() + ")");
        }
        // Mốc 00:00 ngày tập: ngày tập đã tới (hoặc đã qua) thì khoá.
        if (!session.getSessionDate().isAfter(today)) {
            reasons.add("Đã quá hạn đổi lịch (hạn là 00:00 ngày " + session.getSessionDate() + ")");
        }
        if (newDate.isBefore(today)) {
            reasons.add("Không thể dời sang ngày đã qua: " + newDate);
        }
        if (ticket.getExpiresAt() != null && newDate.isAfter(ticket.getExpiresAt().toLocalDate())) {
            reasons.add("Ngày " + newDate + " vượt quá hạn dùng của vé ("
                    + ticket.getExpiresAt().toLocalDate() + ")");
        }
        closedTodayIssue(ticket, newDate, now).ifPresent(reasons::add);

        reject(reasons);
    }

    /**
     * Chi nhánh đã đóng cửa cho ngày HÔM NAY chưa. Trả rỗng với mọi ngày khác:
     * ngày mai trở đi thì giờ giấc hôm nay không nói lên điều gì.
     *
     * <p>Chi nhánh CHƯA khai giờ hoạt động thì bỏ qua luật này. Không có dữ liệu
     * khác với "đóng cửa" — suy diễn ngược lại sẽ khoá đặt lịch trong ngày của
     * mọi chi nhánh chưa kịp cấu hình, một lỗi im lặng không ai truy ra được.
     * Đã khai mà thiếu đúng thứ trong tuần đó thì mới là nghỉ.
     */
    private Optional<String> closedTodayIssue(Ticket ticket, LocalDate date, LocalDateTime now) {
        if (!date.equals(now.toLocalDate()) || ticket.getGymBranch() == null) {
            return Optional.empty();
        }
        List<OperatingHour> hours = operatingHourRepository
                .findByGymBranch_IdOrderByDayOfWeek(ticket.getGymBranch().getId());
        if (hours.isEmpty()) {
            return Optional.empty();
        }
        Optional<OperatingHour> forToday = hours.stream()
                .filter(h -> Objects.equals(h.getDayOfWeek(), date.getDayOfWeek().getValue()))
                .findFirst();
        if (forToday.isEmpty() || forToday.get().isClosed()) {
            return Optional.of("Chi nhánh nghỉ hôm nay — vui lòng chọn ngày khác");
        }
        LocalTime closeTime = forToday.get().getCloseTime();
        if (closeTime != null && !now.toLocalTime().isBefore(closeTime)) {
            return Optional.of("Chi nhánh đã đóng cửa hôm nay (đóng lúc " + closeTime
                    + ") — vui lòng chọn ngày khác");
        }
        return Optional.empty();
    }

    /** Điều kiện chung: vé dùng được và phòng gym còn nhận khách. */
    private List<String> ticketIssues(Ticket ticket, LocalDate today) {
        List<String> reasons = new ArrayList<>();
        if (ticket.getStatus() != TicketStatus.ACTIVE) {
            reasons.add("Vé không ở trạng thái sử dụng được (hiện: " + ticket.getStatus() + ")");
        }
        if (ticket.getExpiresAt() != null && ticket.getExpiresAt().isBefore(LocalDateTime.now())) {
            reasons.add("Vé đã hết hạn ngày " + ticket.getExpiresAt().toLocalDate());
        }
        if (ticket.getGymProfile().getVerificationStatus() != VerificationStatus.APPROVED
                || !ticket.getGymProfile().isActive()) {
            reasons.add("Phòng gym hiện không nhận đặt lịch");
        }
        return reasons;
    }

    private void reject(List<String> reasons) {
        if (!reasons.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Không thể đặt lịch: " + String.join("; ", reasons));
        }
    }
}
