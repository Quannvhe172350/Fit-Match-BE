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
import java.util.HashMap;
import java.util.LinkedHashMap;
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
    public List<PtSlotCellDto> search(Long branchId, LocalDate date, LocalTime startTime,
                                      Integer minutes) {
        return buildCells(branchId, null, date, date, minutes).stream()
                .filter(c -> startTime.equals(c.getStartTime()))
                // Chọn giờ trước thì chỉ hiện PT thật sự còn trống — ô đã kín ở
                // chiều này là nhiễu, khác với lưới (nơi ô mờ vẫn có ý nghĩa).
                .filter(c -> !c.isTaken())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PtSlotCellDto> grid(Long branchId, Long ptId, LocalDate from, LocalDate to,
                                    Integer minutes) {
        if (to.isBefore(from)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "to must not be before from");
        }
        if (from.datesUntil(to.plusDays(1)).count() > MAX_RANGE_DAYS) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Khoảng ngày tối đa " + MAX_RANGE_DAYS + " ngày");
        }
        return buildCells(branchId, ptId, from, to, minutes);
    }

    // ---------- lõi ----------

    /**
     * Sinh mọi ô lưới trong khoảng: ca đã xếp -> cắt thành slot -> bỏ slot bị
     * đơn nghỉ đã duyệt phủ -> đánh dấu slot đã có người đặt.
     *
     * <p>Ba truy vấn cho cả lưới (phân ca, đơn nghỉ, buổi đã đặt) chứ không hỏi
     * theo từng ngày: lưới một tháng của chi nhánh 10 PT là 300+ ô.
     */
    private List<PtSlotCellDto> buildCells(Long branchId, Long ptId, LocalDate from, LocalDate to,
                                           Integer minutes) {
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
        Map<String, List<Busy>> busy = busyRanges(ptIds, from, to);

        /*
         * Gom ca theo (PT, ngày) trước khi cắt slot. Bắt buộc từ khi buổi được
         * phép vắt qua nhiều ca: chuỗi bắt đầu ở ca sáng có thể kết thúc trong ca
         * chiều, nên không thể xét từng ca một cách độc lập như trước.
         */
        Map<String, List<GymShift>> shiftsByPtDate = new LinkedHashMap<>();
        Map<String, PtShiftAssignment> anyRowOf = new LinkedHashMap<>();
        for (PtShiftAssignment row : rows) {
            String k = key(row.getPtProfile().getId(), row.getWorkDate(), null);
            shiftsByPtDate.computeIfAbsent(k, x -> new ArrayList<>()).add(row.getGymShift());
            anyRowOf.putIfAbsent(k, row);
        }

        List<PtSlotCellDto> cells = new ArrayList<>();
        for (Map.Entry<String, List<GymShift>> entry : shiftsByPtDate.entrySet()) {
            PtShiftAssignment row = anyRowOf.get(entry.getKey());
            List<GymShift> shifts = entry.getValue();
            Long rowPtId = row.getPtProfile().getId();
            LocalDate date = row.getWorkDate();
            PtProfile pt = profiles.get(rowPtId);
            List<PtLeaveRequest> leaves = leavesByPt.getOrDefault(rowPtId, List.of());
            List<Busy> busyHere = busy.getOrDefault(key(rowPtId, date, null), List.of());

            for (LocalTime start : candidateStarts(shifts)) {
                List<ShiftSlotResolver.Slot> chain = slotResolver.chainFrom(shifts, start, minutes);
                // Không ghép đủ thời lượng từ mốc này -> không phải một khung đặt
                // được, nên KHÔNG hiện. Hiện ra rồi để BE từ chối là mời một cú
                // bấm không dẫn tới đâu.
                if (chain == null || chain.isEmpty()) {
                    continue;
                }
                // Slot bị nghỉ đã duyệt biến mất khỏi lưới thay vì hiện mờ: ô mờ
                // mang nghĩa "có người đặt rồi", còn PT nghỉ thì đơn giản là
                // không có mặt — hiện ra chỉ gây hiểu nhầm là sắp trống. Chỉ cần
                // MỘT slot trong chuỗi bị phủ là cả khung hỏng.
                if (chain.stream().anyMatch(sl -> slotResolver.anyLeaveCovers(
                        leaves, date, sl.start(), sl.end(), sl.shift()))) {
                    continue;
                }
                LocalTime end = chain.get(chain.size() - 1).end();
                cells.add(PtSlotCellDto.builder()
                        .ptProfileId(rowPtId)
                        .ptName(pt != null ? pt.getDisplayName() : null)
                        .ptAvgRating(pt != null ? pt.getAvgRating() : null)
                        .date(date)
                        .startTime(start)
                        .endTime(end)
                        // Giao khoảng, không so bằng giờ bắt đầu: một buổi dài có
                        // thể chặn nhiều khung bắt đầu khác nhau.
                        .taken(busyHere.stream().anyMatch(b ->
                                slotResolver.overlaps(start, end, b.start(), b.end())))
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

    /** Một khoảng giờ PT đã bận trong ngày. */
    private record Busy(LocalTime start, LocalTime end) {
    }

    /**
     * Khoảng giờ đã bị buổi tập chiếm, theo (PT, ngày) — một truy vấn cho cả lưới.
     *
     * <p>Giữ nguyên KHOẢNG chứ không quy về giờ bắt đầu như bản cũ: buổi nay có
     * thể dài hơn một slot, nên một buổi chặn nhiều khung bắt đầu khác nhau chứ
     * không riêng khung trùng mốc với nó.
     */
    private Map<String, List<Busy>> busyRanges(List<Long> ptIds, LocalDate from, LocalDate to) {
        Map<String, List<Busy>> byPtDate = new HashMap<>();
        for (TrainingSession s : sessionRepository
                .findByPtProfile_IdInAndSessionDateBetweenAndStatusIn(ptIds, from, to, HOLDING)) {
            if (s.getPtSlotStart() == null || s.getPtSlotEnd() == null || s.getPtProfile() == null) {
                continue;
            }
            byPtDate.computeIfAbsent(key(s.getPtProfile().getId(), s.getSessionDate(), null),
                            x -> new ArrayList<>())
                    .add(new Busy(s.getPtSlotStart(), s.getPtSlotEnd()));
        }
        return byPtDate;
    }

    /**
     * Mọi mốc giờ có thể là điểm BẮT ĐẦU trong ngày — hợp của lưới slot mọi ca,
     * đã sắp và khử trùng. Ca liền nhau có thể sinh cùng một mốc (ca sáng kết
     * thúc 12:00, ca chiều bắt đầu 12:00), và mốc đó chỉ được xuất hiện một lần.
     */
    private List<LocalTime> candidateStarts(List<GymShift> shifts) {
        return shifts.stream()
                .flatMap(shift -> slotResolver.slotsOf(shift).stream())
                .map(ShiftSlotResolver.Slot::start)
                .distinct()
                .sorted()
                .toList();
    }

    /** {@code start} = null khi chỉ cần khoá theo (PT, ngày). */
    private static String key(Long ptId, LocalDate date, LocalTime start) {
        return ptId + "|" + date + (start == null ? "" : "T" + start);
    }
}
