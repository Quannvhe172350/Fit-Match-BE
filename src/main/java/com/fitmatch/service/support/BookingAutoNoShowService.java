package com.fitmatch.service.support;

import com.fitmatch.common.AuditActions;
import com.fitmatch.common.enums.BookingStatus;
import com.fitmatch.entity.Booking;
import com.fitmatch.repository.BookingRepository;
import com.fitmatch.service.AuditService;
import com.fitmatch.service.PackageUsageService;
import com.fitmatch.service.SettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * UC-043 (BE-3/C-10, audit 2026-07-17): trước đây no-show phụ thuộc 100% thao tác tay
 * của gym — quên bấm là tiền treo ở heldBalance vô hạn (không vào pending release,
 * không hoàn). Job này đánh NO_SHOW tự động cho booking CONFIRMED đã quá endAt + grace
 * mà khách chưa check-in, với hiệu ứng tài chính y hệt gym bấm tay (settle + tiêu buổi gói).
 * Khách đã check-in thì KHÔNG đụng (đối xứng P1-24) — gym phải complete hoặc hiệu chỉnh.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingAutoNoShowService {

    private final BookingRepository bookingRepository;
    private final BookingLifecycle bookingLifecycle;
    private final SettlementService settlementService;
    private final PackageUsageService packageUsageService;
    private final NotificationDispatcher notificationDispatcher;
    private final AuditService auditService;

    @Value("${app.booking.auto-no-show-grace-hours:24}")
    private long graceHours;

    /** Id các booking đến hạn auto no-show (đọc nhanh, không khóa). */
    @Transactional(readOnly = true)
    public List<Long> findOverdue() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(graceHours);
        return bookingRepository
                .findByStatusAndEndAtBeforeAndCheckedInAtIsNull(BookingStatus.CONFIRMED, cutoff)
                .stream()
                .map(Booking::getId)
                .toList();
    }

    /** Xử lý một booking trong transaction riêng — reload + recheck để idempotent. */
    @Transactional
    public void markOneNoShow(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId).orElse(null);
        if (booking == null || booking.getStatus() != BookingStatus.CONFIRMED
                || booking.getCheckedInAt() != null
                || booking.getEndAt() == null
                || booking.getEndAt().isAfter(LocalDateTime.now().minusHours(graceHours))) {
            return; // đã được gym xử lý tay / check-in trong lúc chờ — bỏ qua
        }
        bookingLifecycle.transition(booking, BookingStatus.NO_SHOW,
                "Auto no-show: quá " + graceHours + "h sau giờ kết thúc, khách chưa check-in");
        settlementService.settleAfterFulfillment(booking, "auto no-show");
        packageUsageService.onBookingFulfilled(booking);
        bookingRepository.save(booking);
        notificationDispatcher.bookingNoShow(booking);
        auditService.record(AuditActions.BOOKING_NO_SHOW, "Booking", bookingId,
                "Auto no-show by system (grace " + graceHours + "h)");
        log.info("Booking {} auto-marked NO_SHOW by system", bookingId);
    }
}
