package com.fitmatch.service.impl;

import com.fitmatch.common.enums.BookingStatus;
import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.dto.booking.SessionNoteRequest;
import com.fitmatch.dto.booking.SessionNoteResponse;
import com.fitmatch.entity.Booking;
import com.fitmatch.entity.SessionNote;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.BookingRepository;
import com.fitmatch.repository.SessionNoteRepository;
import com.fitmatch.service.SessionNoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionNoteServiceImpl implements SessionNoteService {

    /** Chỉ ghi chú khi buổi tập đã chốt/diễn ra — không ghi vào booking nháp. */
    private static final Set<BookingStatus> NOTABLE_STATUSES =
            Set.of(BookingStatus.CONFIRMED, BookingStatus.COMPLETED, BookingStatus.NO_SHOW);

    private final SessionNoteRepository sessionNoteRepository;
    private final BookingRepository bookingRepository;

    @Override
    @Transactional
    public SessionNoteResponse addForGym(String gymUsername, Long bookingId, SessionNoteRequest request) {
        Booking booking = bookingRepository.findByIdAndGymProfile_User_Username(bookingId, gymUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
        return add(booking, request);
    }

    @Override
    @Transactional
    public SessionNoteResponse addForPt(String ptUsername, Long bookingId, SessionNoteRequest request) {
        return add(requireAssignedPt(ptUsername, bookingId), request);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SessionNoteResponse> listForGym(String gymUsername, Long bookingId) {
        bookingRepository.findByIdAndGymProfile_User_Username(bookingId, gymUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
        return list(bookingId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SessionNoteResponse> listForPt(String ptUsername, Long bookingId) {
        requireAssignedPt(ptUsername, bookingId);
        return list(bookingId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SessionNoteResponse> listForCustomer(String customerUsername, Long bookingId) {
        bookingRepository.findByIdAndCustomer_Username(bookingId, customerUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
        return list(bookingId);
    }

    private SessionNoteResponse add(Booking booking, SessionNoteRequest request) {
        if (!NOTABLE_STATUSES.contains(booking.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Session notes can only be added to a CONFIRMED/COMPLETED/NO_SHOW booking (current: "
                            + booking.getStatus() + ")");
        }
        SessionNote note = sessionNoteRepository.save(SessionNote.builder()
                .booking(booking)
                .note(request.getNote())
                .evidenceUrl(request.getEvidenceUrl())
                .build());
        log.info("Session note {} added to booking {}", note.getId(), booking.getId());
        return SessionNoteResponse.of(note);
    }

    private List<SessionNoteResponse> list(Long bookingId) {
        return sessionNoteRepository.findByBooking_IdOrderByIdAsc(bookingId).stream()
                .map(SessionNoteResponse::of).toList();
    }

    private Booking requireAssignedPt(String ptUsername, Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
        if (booking.getPtProfile() == null
                || !booking.getPtProfile().getUser().getUsername().equals(ptUsername)) {
            // 404 thay vì 403: không tiết lộ tồn tại booking của người khác.
            throw new ResourceNotFoundException("Booking", bookingId);
        }
        return booking;
    }
}
