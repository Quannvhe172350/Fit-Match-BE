package com.fitmatch.service.support;

import com.fitmatch.common.enums.SessionStatus;
import com.fitmatch.dto.gym.ConflictingSessionDto;
import com.fitmatch.entity.GymShift;
import com.fitmatch.entity.PtShiftAssignment;
import com.fitmatch.entity.TrainingSession;
import com.fitmatch.repository.TrainingSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Tìm buổi tập của KHÁCH đang vướng một thao tác lịch của Gym.
 *
 * <p>Dùng chung cho ba chỗ chặn (xoá ca, gỡ phân ca, gỡ phân công PT) và một
 * chỗ không chặn mà chuyển hướng (duyệt đơn nghỉ — quyết định §4.1). Gom lại
 * một nơi vì bốn chỗ này phải trả lời GIỐNG HỆT nhau câu "buổi nào bị ảnh
 * hưởng"; lệch nhau thì Gym chặn được ở chỗ này nhưng để lọt ở chỗ kia.
 */
@Component
@RequiredArgsConstructor
public class ShiftConflictFinder {

    private final TrainingSessionRepository sessionRepository;

    /** Buổi SCHEDULED của PT nằm trong khung giờ của ca, đúng ngày đó. */
    public List<TrainingSession> sessionsInShift(Long ptId, LocalDate date, GymShift shift) {
        return sessionRepository
                .findByPtProfile_IdAndSessionDateAndStatusIn(ptId, date, List.of(SessionStatus.SCHEDULED))
                .stream()
                .filter(s -> s.getPtSlotStart() != null)
                .filter(s -> withinShift(s.getPtSlotStart(), shift))
                .toList();
    }

    /** Buổi SCHEDULED vướng cả một lô phân ca — dùng khi xoá ca hoặc gỡ nhiều ngày. */
    public List<TrainingSession> sessionsInAssignments(Collection<PtShiftAssignment> assignments) {
        List<TrainingSession> found = new ArrayList<>();
        for (PtShiftAssignment assignment : assignments) {
            found.addAll(sessionsInShift(assignment.getPtProfile().getId(),
                    assignment.getWorkDate(), assignment.getGymShift()));
        }
        return found;
    }

    /**
     * Giờ bắt đầu của buổi có rơi vào ca không. So theo giờ BẮT ĐẦU chứ không
     * so trọn khoảng: một buổi luôn nằm gọn trong ca sinh ra nó, và ca không
     * chồng nhau nên không có chuyện một giờ thuộc hai ca.
     */
    public boolean withinShift(LocalTime slotStart, GymShift shift) {
        return !slotStart.isBefore(shift.getStartTime()) && slotStart.isBefore(shift.getEndTime());
    }

    public ConflictingSessionDto toDto(TrainingSession s) {
        return ConflictingSessionDto.builder()
                .sessionId(s.getId())
                .date(s.getSessionDate())
                .slotStart(s.getPtSlotStart())
                .slotEnd(s.getPtSlotEnd())
                .customerName(s.getTicket() != null && s.getTicket().getCustomer() != null
                        ? s.getTicket().getCustomer().getFullName() : null)
                .ptName(s.getPtProfile() != null ? s.getPtProfile().getDisplayName() : null)
                .build();
    }

    /** Thông điệp gọn cho BusinessException — "12/09 18:00 (Nguyễn Văn A)". */
    public String describe(Collection<TrainingSession> sessions) {
        return sessions.stream()
                .map(s -> s.getSessionDate() + " " + s.getPtSlotStart()
                        + (s.getTicket() != null && s.getTicket().getCustomer() != null
                        ? " (" + s.getTicket().getCustomer().getFullName() + ")" : ""))
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }
}
