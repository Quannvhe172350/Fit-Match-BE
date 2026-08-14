package com.fitmatch.service.impl;

import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.SessionStatus;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.ticket.ConfirmPtSessionRequest;
import com.fitmatch.dto.ticket.GymCalendarDayResponse;
import com.fitmatch.dto.ticket.TicketResponse;
import com.fitmatch.dto.ticket.TrainingSessionResponse;
import com.fitmatch.entity.GymBranch;
import com.fitmatch.entity.GymProfile;
import com.fitmatch.entity.Ticket;
import com.fitmatch.entity.TrainingSession;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.GymBranchRepository;
import com.fitmatch.repository.TicketRepository;
import com.fitmatch.repository.TrainingSessionRepository;
import com.fitmatch.service.GymTicketService;
import com.fitmatch.service.support.GymProfileResolver;
import com.fitmatch.service.support.NotificationDispatcher;
import com.fitmatch.service.support.SessionLifecycle;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class GymTicketServiceImpl implements GymTicketService {

    /** Trần khoảng ngày một lần hỏi — chặn client vô tình kéo cả năm. */
    private static final int MAX_RANGE_DAYS = 92;

    private final TrainingSessionRepository sessionRepository;
    private final TicketRepository ticketRepository;
    private final GymBranchRepository gymBranchRepository;
    private final GymProfileResolver gymProfileResolver;
    private final SessionLifecycle sessionLifecycle;
    private final NotificationDispatcher notificationDispatcher;

    @Override
    @Transactional(readOnly = true)
    public List<GymCalendarDayResponse> calendar(String gymUsername, Long branchId,
                                                 LocalDate from, LocalDate to) {
        GymProfile gym = gymProfileResolver.requireApprovedGym(gymUsername);
        requireOwnedBranch(gym, branchId);
        assertRange(from, to);

        // Gom theo ngày ở SERVER. TreeMap để ngày ra đúng thứ tự mà không phải
        // sort lại ở FE, và ngày trống vẫn xuất hiện nếu FE cần khung lịch đầy.
        Map<LocalDate, List<GymCalendarDayResponse.Entry>> byDate = new TreeMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            byDate.put(d, new ArrayList<>());
        }
        sessionRepository
                .findByGymBranch_IdAndSessionDateBetweenAndStatusInOrderBySessionDateAscPtSlotStartAsc(
                        branchId, from, to, List.of(SessionStatus.SCHEDULED, SessionStatus.DONE))
                .forEach(s -> byDate.computeIfAbsent(s.getSessionDate(), k -> new ArrayList<>())
                        .add(GymCalendarDayResponse.Entry.of(s)));

        return byDate.entrySet().stream()
                .map(e -> GymCalendarDayResponse.builder()
                        .date(e.getKey())
                        .sessions(e.getValue())
                        .build())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TicketResponse> tickets(String gymUsername, Long branchId,
                                                LocalDate from, LocalDate to, Pageable pageable) {
        GymProfile gym = gymProfileResolver.requireApprovedGym(gymUsername);
        if (branchId != null) {
            requireOwnedBranch(gym, branchId);
        }
        Specification<Ticket> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("gymProfile").get("id"), gym.getId()));
            if (branchId != null) {
                predicates.add(cb.equal(root.get("gymBranch").get("id"), branchId));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from.atStartOfDay()));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), to.plusDays(1).atStartOfDay()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return PageResponse.of(ticketRepository.findAll(spec, pageable), TicketResponse::of);
    }

    @Override
    @Transactional
    public TrainingSessionResponse confirmPtSession(String gymUsername, Long sessionId,
                                                    ConfirmPtSessionRequest request) {
        gymProfileResolver.requireApprovedGym(gymUsername);
        TrainingSession session = sessionRepository
                .findByIdAndTicket_GymProfile_User_Username(sessionId, gymUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Training session", sessionId));

        if (session.getPtProfile() == null) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Buổi tập này không có PT để xác nhận");
        }
        if (session.getPtConfirmedAt() != null) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Buổi tập đã được xác nhận");
        }
        // Cố ý KHÔNG kiểm trạng thái buổi: gym thường xác nhận sau khi buổi đã
        // DONE. Đây là ghi nhận bằng chứng, không phải điều khiển vòng đời.
        session.setPtConfirmedAt(LocalDateTime.now());
        session.setPtConfirmedBy(gymUsername);
        session.setEvidenceUrl(request.getEvidenceUrl());
        sessionLifecycle.recordNote(session, "Gym xác nhận PT có mặt kèm ảnh");
        sessionRepository.save(session);

        notificationDispatcher.ptSessionConfirmed(session);
        log.info("Gym {} confirmed PT attendance for session {}", gymUsername, sessionId);
        return TrainingSessionResponse.of(session);
    }

    private void assertRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Khoảng ngày không hợp lệ");
        }
        if (java.time.temporal.ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Khoảng ngày tối đa " + MAX_RANGE_DAYS + " ngày mỗi lần");
        }
    }

    private GymBranch requireOwnedBranch(GymProfile gym, Long branchId) {
        GymBranch branch = gymBranchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Gym branch", branchId));
        if (!branch.getGymProfile().getId().equals(gym.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Chi nhánh không thuộc phòng gym của bạn");
        }
        return branch;
    }
}
