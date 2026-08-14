package com.fitmatch.service.support;

import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.SessionStatus;
import com.fitmatch.common.enums.TicketKind;
import com.fitmatch.common.enums.TicketStatus;
import com.fitmatch.common.enums.VerificationStatus;
import com.fitmatch.entity.Ticket;
import com.fitmatch.entity.TrainingSession;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.repository.TrainingSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Điều kiện đặt lịch ở cấp VÉ — nửa thứ nhất của {@code BookingEligibilityChecker}
 * cũ. Cố ý không biết gì về PT: buổi tập không có PT chỉ đi qua lớp này và
 * không bao giờ chạm {@link PtSlotValidator}.
 *
 * <p>Những thứ đã biến mất so với mô hình cũ: giờ mở cửa chi nhánh, sức chứa,
 * thời gian báo trước tối thiểu, và ràng buộc "một khách một khung giờ". Vé có
 * giá trị cả ngày nên trùng ngày là chuyện bình thường — khách chỉ được CẢNH
 * BÁO ở tầng API (câu 29), không bị chặn.
 *
 * <p>Mốc thời gian duy nhất còn lại là 00:00 của ngày tập: ngày đã tới thì
 * không dời và không huỷ được nữa.
 */
@Component
@RequiredArgsConstructor
public class SessionSchedulingValidator {

    /** Buổi còn chiếm một ngày của vé; buổi CANCELLED trả lại ngày đó. */
    public static final Set<SessionStatus> CONSUMING_STATUSES =
            Set.of(SessionStatus.SCHEDULED, SessionStatus.DONE);

    private final TrainingSessionRepository trainingSessionRepository;

    /**
     * Kiểm tra vé còn đặt lịch được và các ngày yêu cầu hợp lệ.
     *
     * @param dates các ngày định đặt trong một lần gọi (vé DAY: 1 ngày; vé
     *              PACKAGE: đủ {@code dayCount} ngày liên tiếp)
     */
    public void assertSchedulable(Ticket ticket, List<LocalDate> dates) {
        assertSchedulable(ticket, dates, LocalDate.now());
    }

    /** Bản nhận {@code today} tường minh để test không phụ thuộc đồng hồ máy. */
    public void assertSchedulable(Ticket ticket, List<LocalDate> dates, LocalDate today) {
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
        assertReschedulable(ticket, session, newDate, LocalDate.now());
    }

    /** Bản nhận {@code today} tường minh để test không phụ thuộc đồng hồ máy. */
    public void assertReschedulable(Ticket ticket, TrainingSession session,
                                    LocalDate newDate, LocalDate today) {
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

        reject(reasons);
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
