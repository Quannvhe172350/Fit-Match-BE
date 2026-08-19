package com.fitmatch.service.impl;

import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.LeaveStatus;
import com.fitmatch.common.enums.ShiftSource;
import com.fitmatch.common.enums.VerificationStatus;
import com.fitmatch.dto.gym.PtShiftAssignRequest;
import com.fitmatch.dto.gym.PtShiftAssignResponse;
import com.fitmatch.dto.gym.ShiftRosterCellDto;
import com.fitmatch.dto.pt.PtShiftDto;
import com.fitmatch.entity.GymShift;
import com.fitmatch.entity.OperatingHour;
import com.fitmatch.entity.PtLeaveRequest;
import com.fitmatch.entity.PtProfile;
import com.fitmatch.entity.PtShiftAssignment;
import com.fitmatch.entity.TrainingSession;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.GymShiftRepository;
import com.fitmatch.repository.OperatingHourRepository;
import com.fitmatch.repository.PtAssignmentRepository;
import com.fitmatch.repository.PtLeaveRequestRepository;
import com.fitmatch.repository.PtProfileRepository;
import com.fitmatch.repository.PtShiftAssignmentRepository;
import com.fitmatch.service.PtShiftRosterService;
import com.fitmatch.service.support.ShiftConflictFinder;
import com.fitmatch.service.support.ShiftSlotResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Gym xếp PT vào ca (V86). Cùng một entry point phục vụ cả xếp lặp và xếp lẻ:
 * phần khó — kiểm chồng ca, bỏ ngày đóng cửa, upsert idempotent — giống hệt
 * nhau, tách hai đường chỉ nhân đôi chỗ dễ sai.
 *
 * <p>Thao tác KHÔNG bao giờ thất bại toàn phần vì một ngày lẻ: ngày nào không
 * xếp được thì bỏ và báo lại trong {@link PtShiftAssignResponse}. Gym bấm "xếp
 * cả tháng" mà gặp một ngày lễ thì vẫn phải có 29 ngày còn lại.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PtShiftRosterServiceImpl implements PtShiftRosterService {

    /** Trần một lượt xếp — chặn tai nạn "from 2020 to 2030" quét cả nghìn ngày. */
    private static final int MAX_RANGE_DAYS = 366;

    private final PtShiftAssignmentRepository assignmentRepository;
    private final GymShiftRepository shiftRepository;
    private final PtProfileRepository ptProfileRepository;
    private final PtAssignmentRepository ptAssignmentRepository;
    private final PtLeaveRequestRepository leaveRequestRepository;
    private final OperatingHourRepository operatingHourRepository;
    private final ShiftSlotResolver slotResolver;
    private final ShiftConflictFinder conflictFinder;

    @Override
    @Transactional
    public PtShiftAssignResponse assign(String gymUsername, Long ptId, PtShiftAssignRequest request) {
        PtProfile pt = requireOwnedPt(gymUsername, ptId);
        GymShift shift = shiftRepository
                .findByIdAndGymBranch_GymProfile_User_Username(request.getShiftId(), gymUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Gym shift", request.getShiftId()));

        LocalDate from = request.getFrom();
        LocalDate to = request.getTo();
        if (to.isBefore(from)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "to must not be before from");
        }
        if (from.datesUntil(to.plusDays(1)).count() > MAX_RANGE_DAYS) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Khoảng ngày tối đa " + MAX_RANGE_DAYS + " ngày một lượt");
        }
        if (!shift.isActive()) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Ca " + shift.getName() + " đang tắt — bật ca trước khi xếp PT vào");
        }
        // Câu 24 vẫn là điều kiện nền: PT phải phụ trách đúng chi nhánh của ca,
        // nếu không thì PtSlotValidator sẽ từ chối mọi lượt đặt vào ca này.
        Long branchId = shift.getGymBranch().getId();
        if (!ptAssignmentRepository.existsByPtProfile_IdAndGymBranch_Id(ptId, branchId)) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "PT chưa được phân công vào chi nhánh của ca này — gán chi nhánh trước (UC-022)");
        }

        Set<DayOfWeek> requested = toDays(request.getDaysOfWeek());
        Set<DayOfWeek> shiftDays = shift.daysOfWeekSet();
        Set<DayOfWeek> effective = new LinkedHashSet<>(shiftDays);
        if (!requested.isEmpty()) {
            effective.retainAll(requested);
        }
        if (effective.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Ca " + shift.getName() + " không áp dụng cho thứ nào trong các thứ đã chọn");
        }

        Map<DayOfWeek, OperatingHour> hours = hoursByDay(branchId);
        boolean single = from.equals(to);

        int created = 0;
        List<LocalDate> already = new ArrayList<>();
        List<LocalDate> skippedClosed = new ArrayList<>();
        List<String> skippedOverlap = new ArrayList<>();

        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            if (!effective.contains(date.getDayOfWeek())) {
                continue;
            }
            // Edge case §7.7: chi nhánh đóng cửa thì không sinh ca — nhưng phải
            // nói ra, im lặng bỏ qua sẽ khiến Gym tưởng PT đã có ca ngày đó.
            OperatingHour hour = hours.get(date.getDayOfWeek());
            if (hour == null || hour.isClosed()) {
                skippedClosed.add(date);
                continue;
            }
            if (assignmentRepository
                    .findByPtProfile_IdAndGymShift_IdAndWorkDate(ptId, shift.getId(), date)
                    .isPresent()) {
                already.add(date);
                continue;
            }
            String overlap = overlappingShiftOf(ptId, date, shift);
            if (overlap != null) {
                skippedOverlap.add(date + ": " + overlap);
                continue;
            }
            assignmentRepository.save(PtShiftAssignment.builder()
                    .ptProfile(pt)
                    .gymShift(shift)
                    .workDate(date)
                    .source(single ? ShiftSource.MANUAL : ShiftSource.RECURRING)
                    .active(true)
                    .build());
            created++;
        }

        log.info("Gym {} rostered PT {} into shift {} [{}..{}]: created={}, already={}, closed={}, overlap={}",
                gymUsername, ptId, shift.getId(), from, to, created,
                already.size(), skippedClosed.size(), skippedOverlap.size());

        return PtShiftAssignResponse.builder()
                .created(created)
                .alreadyAssigned(already)
                .skippedClosed(skippedClosed)
                .skippedOverlap(skippedOverlap)
                .build();
    }

    @Override
    @Transactional
    public void unassign(String gymUsername, Long ptId, Long assignmentId) {
        requireOwnedPt(gymUsername, ptId);
        PtShiftAssignment assignment = assignmentRepository
                .findByIdAndPtProfile_GymProfile_User_Username(assignmentId, gymUsername)
                .orElseThrow(() -> new ResourceNotFoundException("PT shift assignment", assignmentId));
        if (!assignment.getPtProfile().getId().equals(ptId)) {
            throw new ResourceNotFoundException("PT shift assignment", assignmentId);
        }

        // Edge case §7.2: gỡ ca đã có khách đặt thì buổi tập mất chỗ dựa —
        // PtSlotValidator sẽ từ chối mọi thao tác sau đó trên buổi ấy. Chặn và
        // trả danh sách để Gym xử lý trước, khác hẳn luồng PT xin nghỉ (§4.1)
        // nơi PT bị động nên hệ thống chuyển việc quyết định sang cho khách.
        List<TrainingSession> conflicts = conflictFinder.sessionsInShift(
                ptId, assignment.getWorkDate(), assignment.getGymShift());
        if (!conflicts.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Không thể gỡ ca: còn " + conflicts.size() + " buổi tập đã đặt ("
                            + conflictFinder.describe(conflicts)
                            + "). Hãy để khách đổi PT hoặc đổi giờ trước.");
        }
        assignmentRepository.delete(assignment);
        log.info("Gym {} removed roster row {} of PT {}", gymUsername, assignmentId, ptId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShiftRosterCellDto> roster(String gymUsername, Long branchId,
                                           LocalDate from, LocalDate to) {
        // Kiểm sở hữu gián tiếp qua ca: chi nhánh không thuộc Gym thì không có
        // ca nào khớp username, nên lưới trả rỗng thay vì lộ dữ liệu Gym khác.
        List<GymShift> shifts = shiftRepository.findByGymBranch_IdOrderByStartTimeAsc(branchId).stream()
                .filter(s -> s.getGymBranch().getGymProfile().getUser().getUsername().equals(gymUsername))
                .toList();
        if (shifts.isEmpty()) {
            return List.of();
        }
        assertRange(from, to);

        List<PtShiftAssignment> rows = assignmentRepository.findRosterOfBranch(branchId, from, to);
        if (rows.isEmpty()) {
            return List.of();
        }
        Set<Long> ptIds = rows.stream().map(r -> r.getPtProfile().getId()).collect(Collectors.toSet());
        Map<Long, List<PtLeaveRequest>> leavesByPt = leaveRequestRepository
                .findOverlappingForPts(ptIds, LeaveStatus.APPROVED, from, to).stream()
                .collect(Collectors.groupingBy(r -> r.getPtProfile().getId()));

        List<ShiftRosterCellDto> cells = new ArrayList<>(rows.size());
        for (PtShiftAssignment row : rows) {
            GymShift shift = row.getGymShift();
            List<PtLeaveRequest> leaves = leavesByPt.getOrDefault(row.getPtProfile().getId(), List.of());
            boolean onLeave = slotResolver.anyLeaveCovers(leaves, row.getWorkDate(),
                    shift.getStartTime(), shift.getEndTime(), shift);
            cells.add(ShiftRosterCellDto.builder()
                    .assignmentId(row.getId())
                    .ptProfileId(row.getPtProfile().getId())
                    .ptName(row.getPtProfile().getDisplayName())
                    .date(row.getWorkDate())
                    .shiftId(shift.getId())
                    .shiftName(shift.getName())
                    .startTime(shift.getStartTime())
                    .endTime(shift.getEndTime())
                    .source(row.getSource() != null ? row.getSource().name() : null)
                    .active(row.isActive())
                    .onLeave(onLeave)
                    .bookedSessions(conflictFinder
                            .sessionsInShift(row.getPtProfile().getId(), row.getWorkDate(), shift).size())
                    .build());
        }
        return cells;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PtShiftDto> myShifts(String ptUsername, LocalDate from, LocalDate to) {
        PtProfile pt = ptProfileRepository.findByUser_Username(ptUsername)
                .orElseThrow(() -> new ResourceNotFoundException("PT profile for user", ptUsername));
        assertRange(from, to);

        List<PtLeaveRequest> leaves = leaveRequestRepository.findOverlapping(
                pt.getId(), Set.of(LeaveStatus.APPROVED), from, to);

        return assignmentRepository.findActiveByPtBetween(pt.getId(), from, to).stream()
                .map(row -> {
                    GymShift shift = row.getGymShift();
                    return PtShiftDto.builder()
                            .date(row.getWorkDate())
                            .shiftId(shift.getId())
                            .shiftName(shift.getName())
                            .branchId(shift.getGymBranch().getId())
                            .branchName(shift.getGymBranch().getName())
                            .startTime(shift.getStartTime())
                            .endTime(shift.getEndTime())
                            .slotMinutes(shift.getSlotMinutes())
                            .onLeave(slotResolver.anyLeaveCovers(leaves, row.getWorkDate(),
                                    shift.getStartTime(), shift.getEndTime(), shift))
                            .bookedSessions(conflictFinder
                                    .sessionsInShift(pt.getId(), row.getWorkDate(), shift).size())
                            .build();
                })
                .toList();
    }

    @Override
    @Transactional
    public int deactivateFutureShifts(Long ptId) {
        List<PtShiftAssignment> future = assignmentRepository
                .findByPtProfile_IdAndActiveTrueAndWorkDateGreaterThanEqual(ptId, LocalDate.now());
        for (PtShiftAssignment row : future) {
            row.setActive(false);
        }
        if (!future.isEmpty()) {
            assignmentRepository.saveAll(future);
            log.info("Deactivated {} future roster row(s) of PT {}", future.size(), ptId);
        }
        return future.size();
    }

    // ---------- helpers ----------

    /**
     * PT đã có ca nào CHỒNG GIỜ trong ngày đó chưa — tính cả ca của chi nhánh
     * khác, vì một người không thể đứng ở hai phòng tập cùng lúc.
     *
     * @return mô tả ca vướng, hoặc null nếu không vướng
     */
    private String overlappingShiftOf(Long ptId, LocalDate date, GymShift candidate) {
        for (PtShiftAssignment existing : assignmentRepository.findActiveByPtAndDate(ptId, date)) {
            GymShift other = existing.getGymShift();
            if (other.getId().equals(candidate.getId())) {
                continue;
            }
            if (slotResolver.shiftsOverlap(candidate, other)) {
                return "đã có ca " + other.getName() + " (" + other.getStartTime() + "-"
                        + other.getEndTime() + ") tại " + other.getGymBranch().getName();
            }
        }
        return null;
    }

    private Map<DayOfWeek, OperatingHour> hoursByDay(Long branchId) {
        return operatingHourRepository.findByGymBranch_IdOrderByDayOfWeek(branchId).stream()
                .filter(h -> h.getDayOfWeek() != null && h.getDayOfWeek() >= 1 && h.getDayOfWeek() <= 7)
                .collect(Collectors.toMap(h -> DayOfWeek.of(h.getDayOfWeek()), Function.identity(),
                        (a, b) -> a, HashMap::new));
    }

    private Set<DayOfWeek> toDays(List<Integer> values) {
        Set<DayOfWeek> days = new LinkedHashSet<>();
        if (values != null) {
            for (Integer value : values) {
                if (value != null && value >= 1 && value <= 7) {
                    days.add(DayOfWeek.of(value));
                }
            }
        }
        return days;
    }

    private void assertRange(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "to must not be before from");
        }
        if (from.datesUntil(to.plusDays(1)).count() > MAX_RANGE_DAYS) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Khoảng ngày tối đa " + MAX_RANGE_DAYS + " ngày");
        }
    }

    private PtProfile requireOwnedPt(String gymUsername, Long ptId) {
        PtProfile pt = ptProfileRepository.findByIdAndGymProfile_User_Username(ptId, gymUsername)
                .orElseThrow(() -> new ResourceNotFoundException("PT profile", ptId));
        if (pt.getGymProfile() != null
                && pt.getGymProfile().getVerificationStatus() != VerificationStatus.APPROVED) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Gym must be APPROVED to manage its trainers (current: "
                            + pt.getGymProfile().getVerificationStatus() + ")");
        }
        return pt;
    }
}
