package com.fitmatch.service;

import com.fitmatch.common.enums.BookingStatus;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.booking.BookingResponse;
import com.fitmatch.dto.booking.BookingStatusHistoryResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * UC-045: tra cứu booking, timeline và chi tiết cho Customer / Gym / PT / Admin.
 */
public interface BookingQueryService {

    PageResponse<BookingResponse> myBookings(String customerUsername, BookingStatus status, Pageable pageable);

    /** Lịch của PT đang đăng nhập (booking được gán cho mình). */
    PageResponse<BookingResponse> ptSchedule(String ptUsername, BookingStatus status, Pageable pageable);

    /** Admin xem toàn hệ thống. */
    PageResponse<BookingResponse> adminList(BookingStatus status, Pageable pageable);

    /** Chi tiết — cho phép customer chủ booking, Gym phụ trách, PT được gán hoặc Admin. */
    BookingResponse detail(String username, Long bookingId);

    /** UC-040: timeline trạng thái — cùng quyền truy cập với chi tiết. */
    List<BookingStatusHistoryResponse> history(String username, Long bookingId);
}
