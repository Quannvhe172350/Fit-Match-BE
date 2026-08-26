package com.fitmatch.service.impl;

import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.SessionStatus;
import com.fitmatch.common.enums.TicketKind;
import com.fitmatch.dto.ticket.ScheduleTicketRequest;
import com.fitmatch.dto.ticket.TicketResponse;
import com.fitmatch.dto.ticket.TrainingSessionResponse;
import com.fitmatch.dto.ticket.UpdateSessionPtRequest;
import com.fitmatch.entity.PtProfile;
import com.fitmatch.entity.Ticket;
import com.fitmatch.entity.TrainingSession;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.PtProfileRepository;
import com.fitmatch.repository.TicketRepository;
import com.fitmatch.repository.TrainingSessionRepository;
import com.fitmatch.service.SessionPtCancellationService;
import com.fitmatch.service.TicketSchedulingService;
import com.fitmatch.service.support.NotificationDispatcher;
import com.fitmatch.service.support.PackageDayGenerator;
import com.fitmatch.service.support.PtSlotValidator;
import com.fitmatch.service.support.SessionLifecycle;
import com.fitmatch.service.support.SessionSchedulingValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketSchedulingServiceImpl implements TicketSchedulingService {

    private final TicketRepository ticketRepository;
    private final TrainingSessionRepository sessionRepository;
    private final PtProfileRepository ptProfileRepository;
    private final SessionSchedulingValidator schedulingValidator;
    private final PtSlotValidator ptSlotValidator;
    private final PackageDayGenerator packageDayGenerator;
    private final SessionLifecycle sessionLifecycle;
    private final NotificationDispatcher notificationDispatcher;
    private final SessionPtCancellationService ptCancellationService;

    @Override
    @Transactional
    public TicketResponse schedule(String customerUsername, Long ticketId, ScheduleTicketRequest request) {
        Ticket ticket = requireOwnedTicket(customerUsername, ticketId);
        List<LocalDate> dates = resolveDates(ticket, request);
        schedulingValidator.assertSchedulable(ticket, dates);

        Map<Integer, ScheduleTicketRequest.DayPt> ptByDay = ptByDayIndex(ticket, request);

        // day_index tiếp tục từ những ngày đã đặt trước đó (vé DAY chỉ có 1 ngày,
        // nhưng vé gói huỷ dở rồi đặt lại vẫn phải tránh đụng unique key).
        int nextIndex = (int) sessionRepository.countByTicket_IdAndStatusNot(ticketId, SessionStatus.CANCELLED) + 1;

        List<TrainingSession> created = new ArrayList<>();
        for (int i = 0; i < dates.size(); i++) {
            LocalDate date = dates.get(i);
            int dayIndex = nextIndex + i;
            ScheduleTicketRequest.DayPt pt = ptByDay.get(dayIndex);

            TrainingSession session = TrainingSession.builder()
                    .ticket(ticket)
                    .gymBranch(ticket.getGymBranch())
                    .dayIndex(dayIndex)
                    .sessionDate(date)
                    .status(SessionStatus.SCHEDULED)
                    .build();
            if (pt != null && pt.getPtId() != null) {
                applyPt(ticket, session, pt.getPtId(), pt.getSlotStart(), date);
            }
            created.add(sessionRepository.save(session));
        }

        // startDate là mốc tính hoàn tiền một phần (câu 11) — luôn là ngày sớm nhất.
        LocalDate earliest = dates.stream().min(LocalDate::compareTo).orElseThrow();
        if (ticket.getStartDate() == null || earliest.isBefore(ticket.getStartDate())) {
            ticket.setStartDate(earliest);
        }
        ticketRepository.save(ticket);

        created.forEach(notificationDispatcher::sessionBooked);
        log.info("Customer {} scheduled {} day(s) for ticket {}", customerUsername, created.size(), ticketId);
        return TicketResponse.withSessions(ticket,
                sessionRepository.findByTicket_IdOrderByDayIndexAsc(ticketId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrainingSessionResponse> mySessions(String customerUsername, LocalDate from, LocalDate to) {
        return sessionRepository
                .findByTicket_Customer_UsernameAndSessionDateBetweenAndStatusInOrderBySessionDateAsc(
                        customerUsername, from, to,
                        List.of(SessionStatus.SCHEDULED, SessionStatus.DONE))
                .stream().map(TrainingSessionResponse::of).toList();
    }

    @Override
    @Transactional
    public TrainingSessionResponse updateDate(String customerUsername, Long sessionId, LocalDate date) {
        TrainingSession session = requireOwnedSession(customerUsername, sessionId);
        Ticket ticket = session.getTicket();
        schedulingValidator.assertReschedulable(ticket, session, date);

        LocalDate oldDate = session.getSessionDate();
        if (oldDate.equals(date)) {
            return TrainingSessionResponse.of(session);
        }
        // V85: PT phải có CA phủ đúng khung giờ đó ở ngày mới, nếu không thì 409 và
        // khách chọn lại — thay vì im lặng dời sang một ngày PT không làm việc.
        // Giờ kết thúc lấy lại từ ca của ngày mới vì hai ngày có thể thuộc hai ca
        // với slotMinutes khác nhau.
        if (session.getPtProfile() != null) {
            PtSlotValidator.ResolvedSlot slot = ptSlotValidator.resolveSlot(
                    session.getPtProfile().getId(), ticket.getGymBranch().getId(),
                    date, session.getPtSlotStart(), session.getId(),
                    // Ngày mới có thể thuộc ca khác với slotMinutes khác — vé quy
                    // định bao nhiêu phút thì ngày mới cũng phải đúng ngần ấy.
                    ticket.getMinutesPerDay());
            session.setPtSlotEnd(slot.endTime());
        }
        session.setSessionDate(date);
        if (ticket.getStartDate() != null && date.isBefore(ticket.getStartDate())) {
            ticket.setStartDate(date);
        }
        sessionLifecycle.recordNote(session, "Dời lịch " + oldDate + " -> " + date);
        sessionRepository.save(session);

        notificationDispatcher.sessionRescheduled(session, oldDate);
        return TrainingSessionResponse.of(session);
    }

    @Override
    @Transactional
    public TrainingSessionResponse setPt(String customerUsername, Long sessionId,
                                         UpdateSessionPtRequest request) {
        TrainingSession session = requireOwnedSession(customerUsername, sessionId);
        Ticket ticket = session.getTicket();
        assertEditableSession(session);

        applyPt(ticket, session, request.getPtId(), request.getSlotStart(), session.getSessionDate());
        sessionLifecycle.recordNote(session, "Chọn PT #" + request.getPtId()
                + " khung " + request.getSlotStart());
        sessionRepository.save(session);
        // Quyết định §4.1: nếu buổi này đang chờ khách xử lý vì PT cũ xin nghỉ thì
        // việc chọn được PT mới CHÍNH LÀ câu trả lời — đóng luôn, không bắt khách
        // vào thêm một màn hình nữa để xác nhận.
        ptCancellationService.markReplaced(session.getId());

        notificationDispatcher.sessionPtAssigned(session);
        return TrainingSessionResponse.of(session);
    }

    @Override
    @Transactional
    public TrainingSessionResponse removePt(String customerUsername, Long sessionId) {
        TrainingSession session = requireOwnedSession(customerUsername, sessionId);
        assertEditableSession(session);
        if (session.getPtProfile() == null) {
            return TrainingSessionResponse.of(session);
        }
        // Không hoàn tiền: phụ phí PT được tính theo VÉ (mọi ngày) chứ không theo
        // từng buổi, nên bỏ PT một ngày không sinh khoản phải trả lại.
        Long removedPtId = session.getPtProfile().getId();
        session.setPtProfile(null);
        session.setPtSlotStart(null);
        session.setPtSlotEnd(null);
        sessionLifecycle.recordNote(session, "Bỏ PT #" + removedPtId + " khỏi buổi tập");
        sessionRepository.save(session);
        return TrainingSessionResponse.of(session);
    }

    @Override
    @Transactional
    public TrainingSessionResponse checkIn(String customerUsername, Long sessionId) {
        TrainingSession session = requireOwnedSession(customerUsername, sessionId);
        // Check-in chỉ có nghĩa với buổi có PT: buổi tự tập là vé cả ngày, khách
        // vào lúc nào cũng được và không ai cần ghi nhận.
        if (session.getPtProfile() == null) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Chỉ check-in được cho buổi tập có PT");
        }
        if (session.getStatus() != SessionStatus.SCHEDULED) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Buổi tập không còn ở trạng thái đặt trước (hiện: " + session.getStatus() + ")");
        }
        if (!session.getSessionDate().equals(LocalDate.now())) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Chỉ check-in được trong ngày tập (" + session.getSessionDate() + ")");
        }
        if (session.getCheckedInAt() != null) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Buổi tập đã được check-in");
        }
        session.setCheckedInAt(LocalDateTime.now());
        // Buổi vẫn tiêu theo ngày (câu 9) — check-in KHÔNG đổi trạng thái.
        sessionLifecycle.recordNote(session, "Khách check-in");
        sessionRepository.save(session);
        return TrainingSessionResponse.of(session);
    }

    // ---------- helpers ----------

    private List<LocalDate> resolveDates(Ticket ticket, ScheduleTicketRequest request) {
        if (ticket.getKind() == TicketKind.DAY) {
            if (request.getDate() == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "date is required for a DAY ticket");
            }
            return List.of(request.getDate());
        }
        if (request.getStartDate() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "startDate is required for a PACKAGE ticket");
        }
        return packageDayGenerator.generate(request.getStartDate(), ticket.getDayCount());
    }

    /** Gộp PT của vé DAY (trường phẳng) và vé PACKAGE (danh sách) về cùng một map. */
    private Map<Integer, ScheduleTicketRequest.DayPt> ptByDayIndex(Ticket ticket,
                                                                  ScheduleTicketRequest request) {
        Map<Integer, ScheduleTicketRequest.DayPt> map = new HashMap<>();
        if (ticket.getKind() == TicketKind.DAY) {
            if (request.getPtId() != null) {
                map.put(1, ScheduleTicketRequest.DayPt.builder()
                        .dayIndex(1).ptId(request.getPtId()).slotStart(request.getSlotStart()).build());
            }
            return map;
        }
        if (request.getDays() != null) {
            for (ScheduleTicketRequest.DayPt day : request.getDays()) {
                if (day.getDayIndex() == null) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR, "dayIndex is required");
                }
                map.put(day.getDayIndex(), day);
            }
        }
        return map;
    }

    /** Gán PT + khung giờ vào một buổi, sau khi qua đủ năm điều kiện của PtSlotValidator. */
    private void applyPt(Ticket ticket, TrainingSession session, Long ptId,
                         LocalTime slotStart, LocalDate date) {
        if (!ticket.isWithPt()) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Vé này không kèm PT — mua vé có PT để chọn huấn luyện viên");
        }
        if (slotStart == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "slotStart is required when a PT is selected");
        }
        PtSlotValidator.ResolvedSlot slot = ptSlotValidator.resolveSlot(
                ptId, ticket.getGymBranch().getId(), date, slotStart, session.getId(),
                ticket.getMinutesPerDay());
        PtProfile pt = ptProfileRepository.findById(ptId)
                .orElseThrow(() -> new ResourceNotFoundException("PT profile", ptId));

        session.setPtProfile(pt);
        session.setPtSlotStart(slot.startTime());
        // Giờ kết thúc do CA quyết (slotMinutes) — khách chỉ chọn giờ bắt đầu.
        session.setPtSlotEnd(slot.endTime());
    }

    /** Buổi chỉ sửa được khi còn SCHEDULED và ngày tập chưa tới (mốc 00:00). */
    private void assertEditableSession(TrainingSession session) {
        if (session.getStatus() != SessionStatus.SCHEDULED) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Buổi tập không còn ở trạng thái đặt trước (hiện: " + session.getStatus() + ")");
        }
        if (!session.getSessionDate().isAfter(LocalDate.now())) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Đã quá hạn chỉnh sửa (hạn là 00:00 ngày " + session.getSessionDate() + ")");
        }
    }

    private Ticket requireOwnedTicket(String customerUsername, Long ticketId) {
        return ticketRepository.findByIdAndCustomer_Username(ticketId, customerUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));
    }

    private TrainingSession requireOwnedSession(String customerUsername, Long sessionId) {
        return sessionRepository.findByIdAndTicket_Customer_Username(sessionId, customerUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Training session", sessionId));
    }
}
