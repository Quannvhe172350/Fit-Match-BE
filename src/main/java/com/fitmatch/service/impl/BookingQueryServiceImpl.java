package com.fitmatch.service.impl;

import com.fitmatch.common.enums.BookingStatus;
import com.fitmatch.common.enums.Role;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.booking.BookingResponse;
import com.fitmatch.dto.booking.BookingStatusHistoryResponse;
import com.fitmatch.entity.Booking;
import com.fitmatch.entity.User;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.BookingRepository;
import com.fitmatch.repository.BookingStatusHistoryRepository;
import com.fitmatch.repository.UserRepository;
import com.fitmatch.service.BookingQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BookingQueryServiceImpl implements BookingQueryService {

    private final BookingRepository bookingRepository;
    private final BookingStatusHistoryRepository historyRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> myBookings(String customerUsername, BookingStatus status,
                                                    Pageable pageable) {
        var page = status != null
                ? bookingRepository.findByCustomer_UsernameAndStatus(customerUsername, status, pageable)
                : bookingRepository.findByCustomer_Username(customerUsername, pageable);
        return PageResponse.of(page, BookingResponse::of);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> ptSchedule(String ptUsername, BookingStatus status, Pageable pageable) {
        var page = status != null
                ? bookingRepository.findByPtProfile_User_UsernameAndStatus(ptUsername, status, pageable)
                : bookingRepository.findByPtProfile_User_Username(ptUsername, pageable);
        return PageResponse.of(page, BookingResponse::of);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> adminList(BookingStatus status, Pageable pageable) {
        var page = status != null
                ? bookingRepository.findByStatus(status, pageable)
                : bookingRepository.findAll(pageable);
        return PageResponse.of(page, BookingResponse::of);
    }

    @Override
    @Transactional(readOnly = true)
    public BookingResponse detail(String username, Long bookingId) {
        return BookingResponse.of(requireAccessible(username, bookingId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingStatusHistoryResponse> history(String username, Long bookingId) {
        requireAccessible(username, bookingId);
        return historyRepository.findByBooking_IdOrderByCreatedAtAsc(bookingId).stream()
                .map(BookingStatusHistoryResponse::of).toList();
    }

    /**
     * UC-045: chỉ các bên liên quan được xem — customer chủ booking, operator của Gym
     * phụ trách, PT được gán, hoặc Admin. Trả 404 (không lộ tồn tại) nếu không có quyền.
     */
    private Booking requireAccessible(String username, Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", username));

        boolean isCustomer = booking.getCustomer().getUsername().equals(username);
        boolean isGymOperator = booking.getGymProfile().getUser() != null
                && booking.getGymProfile().getUser().getUsername().equals(username);
        boolean isAssignedPt = booking.getPtProfile() != null
                && booking.getPtProfile().getUser() != null
                && booking.getPtProfile().getUser().getUsername().equals(username);
        boolean isAdmin = user.getRole() == Role.ROLE_ADMIN;

        if (!(isCustomer || isGymOperator || isAssignedPt || isAdmin)) {
            throw new ResourceNotFoundException("Booking", bookingId);
        }
        return booking;
    }
}
