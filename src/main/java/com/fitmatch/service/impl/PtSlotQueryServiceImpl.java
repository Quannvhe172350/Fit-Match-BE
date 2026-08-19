package com.fitmatch.service.impl;

import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.LeaveStatus;
import com.fitmatch.common.enums.PtStatus;
import com.fitmatch.common.enums.SessionStatus;
import com.fitmatch.dto.pt.PtSlotCellDto;
import com.fitmatch.entity.GymShift;
import com.fitmatch.entity.PtLeaveRequest;
import com.fitmatch.entity.PtProfile;
import com.fitmatch.entity.PtShiftAssignment;
import com.fitmatch.entity.TrainingSession;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.repository.PtAssignmentRepository;
import com.fitmatch.repository.PtLeaveRequestRepository;
import com.fitmatch.repository.PtProfileRepository;
import com.fitmatch.repository.PtShiftAssignmentRepository;
import com.fitmatch.repository.TrainingSessionRepository;
import com.fitmatch.service.PtSlotQueryService;
import com.fitmatch.service.support.ShiftSlotResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Lưới chọn PT của khách. Thay {@code PtDailyAvailabilityServiceImpl}:
 * {@link PtSlotCellDto} ra ngoài giữ nguyên từng trường, chỉ NGUỒN dữ liệu đổi
 * — trước là khung giờ PT tự khai, giờ là ca Gym đã xếp trừ đi đơn nghỉ đã duyệt.
 *
 * <p>Giữ nguyên contract là chủ ý: toàn bộ FE phía khách
 * ({@code pt-availability-grid}, {@code day-composer}, {@code ticket-schedule})
 * không phải sửa gì ngoài nhãn, nên phần dễ vỡ nhất của FE không bị đụng vào
 * trong cùng đợt đổi mô hình.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PtSlotQueryServiceImpl implements PtSlotQueryService {

    /** Trần khoảng ngày một lượt hỏi lưới — bằng lịch của gym (92 ngày). */
    private static final int MAX_RANGE_DAYS = 92;

    private static final Set<SessionStatus> HOLDING =
            Set.of(SessionStatus.SCHEDULED, SessionStatus.DONE);

    private final PtShiftAssignmentRepository assignmentRepository;
    private final PtLeaveRequestRepository leaveRequestRepository;
    private final PtProfileRepository ptProfileRepository;
    private final PtAssignmentRepository ptAssignmentRepository;
    private final TrainingSessionRepository sessionRepository;
    private final ShiftSlotResolver slotResolver;

    @Override
    @Transactional(readOnly = true)
    public List<PtSlotCellDto> search(Long branchId, LocalDate date, LocalTime startTime) {
        return buildCells(branchId, null, date, date).stream()
                .filter(c -> startTime.equals(c.getStartTime()))
                // Chọn giờ trước thì chỉ hiện PT thật sự còn trống — ô đã kín ở
                // chiều này là nhiễu, khác với lưới (nơi ô mờ vẫn có ý nghĩa).
                .filter(c -> !c.isTaken())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PtSlotCellDto> grid(Long branchId, Long ptId, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "to must not be before from");
        }
        if (from.datesUntil(to.plusDays(1)).count() > MAX_RANGE_DAYS) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Khoảng ngày tối đa " + MAX_RANGE_DAYS + " ngày");
        }
        return buildCells(branchId, ptId, from, to);
    }

    // ---------- lõi ----------

    /**
     * Sinh mọi ô lưới trong khoảng: ca đã xếp -> cắt thành slot -> bỏ slot bị
     * đơn nghỉ đã duyệt phủ -> đánh dấu slot đã có người đặt.
     *
     * <p>Ba truy vấn cho cả lưới (phân ca, đơn nghỉ, buổi đã đặt) chứ không hỏi
     * theo từng ngày: lưới một tháng của chi nhánh 10 PT là 300+ ô.
     */
    private List<PtSlotCellDto> buildCells(Long branchId, Long ptId, LocalDate from, LocalDate to) {
        List<Long> ptIds = activePtIdsOfBranch(branchId);
        if (ptId != null) {
            if (!ptIds.contains(ptId)) {
                throw new BusinessException(ErrorCode.INVALID_STATE,
                        "PT không phụ trách chi nhánh này hoặc đang ngừng nhận lịch");
            }
            ptIds = List.of(ptId);
        }
        if (ptIds.isEmpty()) {
            return List.of();
        }

        List<PtShiftAssignment> rows =
                assignmentRepository.findActiveByPtsAndBranchBetween(ptIds, branchId, from, to);
        if (rows.isEmpty()) {
            return List.of();
        }

        Map<Long, PtProfile> profiles = ptProfileRepository.findAllById(ptIds).stream()
                .collect(Collectors.toMap(PtProfile::getId, Function.identity()));
        Map<Long, List<PtLeaveRequest>> leavesByPt = leaveRequestRepository
                .findOverlappingForPts(ptIds, LeaveStatus.APPROVED, from, to).stream()
                .collect(Collectors.groupingBy(r -> r.getPtProfile().getId()));
        Set<String> taken = takenKeys(ptIds, from, to);

        List<PtSlotCellDto> cells = new ArrayList<>();
        for (PtShiftAssignment row : rows) {
            GymShift shift = row.getGymShift();
            Long rowPtId = row.getPtProfile().getId();
            PtProfile pt = profiles.get(rowPtId);
            List<PtLeaveRequest> leaves = leavesByPt.getOrDefault(rowPtId, List.of());

            for (ShiftSlotResolver.Slot slot : slotResolver.slotsOf(shift)) {
                // Slot bị nghỉ đã duyệt biến mất khỏi lưới thay vì hiện mờ: ô mờ
                // mang nghĩa "có người đặt rồi", còn PT nghỉ thì đơn giản là
                // không có mặt — hiện ra chỉ gây hiểu nhầm là sắp trống.
                if (slotResolver.anyLeaveCovers(leaves, row.getWorkDate(),
                        slot.start(), slot.end(), shift)) {
                    continue;
                }
                cells.add(PtSlotCellDto.builder()
                        .ptProfileId(rowPtId)
                        .ptName(pt != null ? pt.getDisplayName() : null)
                        .ptAvgRating(pt != null ? pt.getAvgRating() : null)
                        .date(row.getWorkDate())
                        .startTime(slot.start())
                        .endTime(slot.end())
                        .taken(taken.contains(key(rowPtId, row.getWorkDate(), slot.start())))
                        .build());
            }
        }
        cells.sort((a, b) -> {
            int byDate = a.getDate().compareTo(b.getDate());
            return byDate != 0 ? byDate : a.getStartTime().compareTo(b.getStartTime());
        });
        return cells;
    }

    /** PT của chi nhánh đang ACTIVE — PT tạm tắt/đình chỉ không xuất hiện trong lưới. */
    private List<Long> activePtIdsOfBranch(Long branchId) {
        List<Long> assigned = ptAssignmentRepository.findPtIdsByBranchId(branchId).stream()
                .distinct().toList();
        if (assigned.isEmpty()) {
            return List.of();
        }
        return ptProfileRepository.findAllById(assigned).stream()
                .filter(p -> p.getStatus() == PtStatus.ACTIVE)
                .map(PtProfile::getId)
                .toList();
    }

    /** Các ô (pt, ngày, giờ) đã bị buổi tập chiếm — một truy vấn cho cả lưới. */
    private Set<String> takenKeys(List<Long> ptIds, LocalDate from, LocalDate to) {
        Set<String> keys = new HashSet<>();
        for (TrainingSession s : sessionRepository
                .findByPtProfile_IdInAndSessionDateBetweenAndStatusIn(ptIds, from, to, HOLDING)) {
            if (s.getPtSlotStart() != null && s.getPtProfile() != null) {
                keys.add(key(s.getPtProfile().getId(), s.getSessionDate(), s.getPtSlotStart()));
            }
        }
        return keys;
    }

    private static String key(Long ptId, LocalDate date, LocalTime start) {
        return ptId + "|" + date + "T" + start;
    }
}
