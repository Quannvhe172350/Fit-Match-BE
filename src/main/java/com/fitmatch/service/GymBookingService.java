package com.fitmatch.service;

import com.fitmatch.common.enums.BookingStatus;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.booking.BookingResponse;
import org.springframework.data.domain.Pageable;

/**
 * Booking phía Gym (UC-037..039, 041..043).
 */
public interface GymBookingService {

    /** UC-037: hộp thư booking của Gym (mặc định PENDING_GYM chờ xử lý). */
    PageResponse<BookingResponse> list(String gymUsername, BookingStatus status, Pageable pageable);

    /** UC-038: nhận booking (kèm gán PT nếu truyền) -> CONFIRMED. */
    BookingResponse accept(String gymUsername, Long bookingId, Long ptId);

    /** UC-038: từ chối booking kèm lý do -> REJECTED (kích hoạt hoàn tiền ở phase payment). */
    BookingResponse reject(String gymUsername, Long bookingId, String reason);

    /** UC-039: gán/đổi PT phụ trách khi PENDING_GYM hoặc CONFIRMED. */
    BookingResponse assignPt(String gymUsername, Long bookingId, Long ptId);
}
