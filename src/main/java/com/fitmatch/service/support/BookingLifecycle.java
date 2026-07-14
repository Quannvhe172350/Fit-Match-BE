package com.fitmatch.service.support;

import com.fitmatch.common.enums.BookingStatus;
import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.entity.Booking;
import com.fitmatch.entity.BookingStatusHistory;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.repository.BookingStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

import static com.fitmatch.common.enums.BookingStatus.CANCELLED;
import static com.fitmatch.common.enums.BookingStatus.COMPLETED;
import static com.fitmatch.common.enums.BookingStatus.CONFIRMED;
import static com.fitmatch.common.enums.BookingStatus.DRAFT;
import static com.fitmatch.common.enums.BookingStatus.NO_SHOW;
import static com.fitmatch.common.enums.BookingStatus.PENDING_GYM;
import static com.fitmatch.common.enums.BookingStatus.PENDING_PAYMENT;
import static com.fitmatch.common.enums.BookingStatus.REJECTED;

/**
 * State machine tập trung của booking (UC-040): mọi chuyển trạng thái phải đi
 * qua {@link #transition} để được kiểm tra tính hợp lệ và ghi lịch sử.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BookingLifecycle {

    private static final Map<BookingStatus, Set<BookingStatus>> ALLOWED = Map.of(
            DRAFT, Set.of(PENDING_PAYMENT, PENDING_GYM, CANCELLED),
            PENDING_PAYMENT, Set.of(PENDING_GYM, CANCELLED),
            PENDING_GYM, Set.of(CONFIRMED, REJECTED, CANCELLED),
            CONFIRMED, Set.of(COMPLETED, CANCELLED, NO_SHOW),
            REJECTED, Set.of(),
            CANCELLED, Set.of(),
            NO_SHOW, Set.of(),
            COMPLETED, Set.of()
    );

    private final BookingStatusHistoryRepository historyRepository;

    /** Ghi một dòng lịch sử không đổi trạng thái (vd reschedule, đổi PT) — UC-040. */
    public void recordNote(Booking booking, String reason) {
        historyRepository.save(BookingStatusHistory.builder()
                .booking(booking)
                .fromStatus(booking.getStatus())
                .toStatus(booking.getStatus())
                .reason(reason)
                .build());
        log.info("Booking {} note: {}", booking.getId(), reason);
    }

    /** Hai trạng thái kết quả điểm danh có thể hiệu chỉnh lẫn nhau (UC-050). */
    private static final Set<BookingStatus> CORRECTABLE = Set.of(COMPLETED, NO_SHOW);

    /**
     * UC-050: hiệu chỉnh kết quả điểm danh COMPLETED <-> NO_SHOW (ngoài state
     * machine thường). Chỉ dùng cho luồng correct-attendance có lý do + audit;
     * caller phải tự bảo đảm tiền chưa giải ngân/hoàn.
     */
    public void overrideForCorrection(Booking booking, BookingStatus to, String reason) {
        BookingStatus from = booking.getStatus();
        if (!CORRECTABLE.contains(from) || !CORRECTABLE.contains(to) || from == to) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Attendance correction only allows COMPLETED <-> NO_SHOW (from " + from + " to " + to + ")");
        }
        booking.setStatus(to);
        booking.setStatusReason(reason);
        historyRepository.save(BookingStatusHistory.builder()
                .booking(booking)
                .fromStatus(from)
                .toStatus(to)
                .reason(reason)
                .build());
        log.info("Booking {} attendance corrected {} -> {} ({})", booking.getId(), from, to, reason);
    }

    /** Chuyển trạng thái nếu hợp lệ; ghi history; ném 409 nếu chuyển sai luồng. */
    public void transition(Booking booking, BookingStatus to, String reason) {
        BookingStatus from = booking.getStatus();
        if (!ALLOWED.getOrDefault(from, Set.of()).contains(to)) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Cannot move booking from " + from + " to " + to);
        }
        booking.setStatus(to);
        booking.setStatusReason(reason);
        historyRepository.save(BookingStatusHistory.builder()
                .booking(booking)
                .fromStatus(from)
                .toStatus(to)
                .reason(reason)
                .build());
        log.info("Booking {} moved {} -> {} ({})", booking.getId(), from, to, reason);
    }
}
