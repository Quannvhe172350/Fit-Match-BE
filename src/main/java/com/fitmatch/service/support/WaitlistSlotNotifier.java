package com.fitmatch.service.support;

import com.fitmatch.entity.Booking;
import com.fitmatch.entity.WaitlistEntry;
import com.fitmatch.repository.WaitlistEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * UC-044 (E-15/BE-14, audit 2026-07-17): trước đây waitlist hoàn toàn passive —
 * khách chờ không bao giờ biết slot trống ra. Khi một booking giữ chỗ bị hủy/từ chối,
 * báo cho các khách đang chờ cùng dịch vụ/gói (giới hạn số người đầu hàng đợi để
 * tránh dội thông báo; ai đặt trước được trước — không giữ chỗ tự động).
 */
@Component
@RequiredArgsConstructor
public class WaitlistSlotNotifier {

    private static final int MAX_NOTIFIED = 5;

    private final WaitlistEntryRepository waitlistEntryRepository;
    private final NotificationDispatcher notificationDispatcher;

    /** Gọi trong transaction hủy/từ chối — notification thật đi AFTER_COMMIT. */
    public void onSlotFreed(Booking booking) {
        List<WaitlistEntry> waiting;
        String itemName;
        if (booking.getGymService() != null) {
            waiting = waitlistEntryRepository
                    .findByGymService_IdAndActiveTrueOrderByCreatedAtAsc(booking.getGymService().getId());
            itemName = booking.getGymService().getName();
        } else if (booking.getTrainingPackage() != null) {
            waiting = waitlistEntryRepository
                    .findByTrainingPackage_IdAndActiveTrueOrderByCreatedAtAsc(booking.getTrainingPackage().getId());
            itemName = booking.getTrainingPackage().getName();
        } else {
            return; // buổi từ gói đã mua — không có hàng đợi tương ứng
        }
        waiting.stream()
                .filter(w -> booking.getCustomer() == null || w.getCustomer() == null
                        || !w.getCustomer().getId().equals(booking.getCustomer().getId()))
                .limit(MAX_NOTIFIED)
                .forEach(w -> notificationDispatcher.waitlistSlotOpened(w.getCustomer(), itemName));
    }
}
