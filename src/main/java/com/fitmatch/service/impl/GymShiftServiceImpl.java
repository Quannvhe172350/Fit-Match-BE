package com.fitmatch.service.impl;

import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.dto.gym.GymShiftRequest;
import com.fitmatch.dto.gym.GymShiftResponse;
import com.fitmatch.entity.GymBranch;
import com.fitmatch.entity.GymShift;
import com.fitmatch.entity.OperatingHour;
import com.fitmatch.entity.PtShiftAssignment;
import com.fitmatch.entity.TrainingSession;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.GymBranchRepository;
import com.fitmatch.repository.GymShiftRepository;
import com.fitmatch.repository.OperatingHourRepository;
import com.fitmatch.repository.PtShiftAssignmentRepository;
import com.fitmatch.service.GymShiftService;
import com.fitmatch.service.support.ShiftConflictFinder;
import com.fitmatch.service.support.ShiftSlotResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * CRUD ca của chi nhánh. Toàn bộ ràng buộc "ca có hợp lệ không" gom vào một chỗ
 * chứ không rải ra: ca sai giờ mở cửa hoặc chồng ca sẽ sinh ra slot mà khách
 * đặt được nhưng chi nhánh đóng cửa — lỗi chỉ lộ khi khách tới nơi.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GymShiftServiceImpl implements GymShiftService {

    private final GymShiftRepository shiftRepository;
    private final GymBranchRepository branchRepository;
    private final OperatingHourRepository operatingHourRepository;
    private final PtShiftAssignmentRepository shiftAssignmentRepository;
    private final ShiftSlotResolver slotResolver;
    private final ShiftConflictFinder conflictFinder;

    @Override
    @Transactional(readOnly = true)
    public List<GymShiftResponse> list(String gymUsername, Long branchId) {
        requireOwnedBranch(gymUsername, branchId);
        return shiftRepository.findByGymBranch_IdOrderByStartTimeAsc(branchId).stream()
                .map(s -> GymShiftResponse.of(s, slotResolver.slotsOf(s).size()))
                .toList();
    }

    @Override
    @Transactional
    public GymShiftResponse create(String gymUsername, Long branchId, GymShiftRequest request) {
        GymBranch branch = requireOwnedBranch(gymUsername, branchId);

        GymShift shift = GymShift.builder()
                .gymBranch(branch)
                .name(request.getName().trim())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .slotMinutes(request.getSlotMinutes())
                .daysOfWeek(GymShift.toDaysOfWeek(toDays(request.getDaysOfWeek())))
                .active(request.getActive() == null || request.getActive())
                .build();

        validate(shift, branchId, null);
        GymShift saved = shiftRepository.save(shift);
        log.info("Gym {} created shift {} ({} {}-{}) on branch {}", gymUsername, saved.getId(),
                saved.getName(), saved.getStartTime(), saved.getEndTime(), branchId);
        return GymShiftResponse.of(saved, slotResolver.slotsOf(saved).size());
    }

    @Override
    @Transactional
    public GymShiftResponse update(String gymUsername, Long branchId, Long shiftId,
                                   GymShiftRequest request) {
        requireOwnedBranch(gymUsername, branchId);
        GymShift shift = requireShiftOfBranch(shiftId, branchId);

        // Đổi giờ hoặc độ dài slot của ca đã có khách đặt sẽ làm buổi cũ rơi ra
        // ngoài lưới slot mới: khách không dời lịch được nữa và lưới hiển thị
        // lệch với dữ liệu đã lưu trong training_sessions.
        boolean timingChanged = !shift.getStartTime().equals(request.getStartTime())
                || !shift.getEndTime().equals(request.getEndTime())
                || !shift.getSlotMinutes().equals(request.getSlotMinutes());
        if (timingChanged) {
            assertNoBookedSessions(shift, "Không thể đổi giờ của ca");
        }

        shift.setName(request.getName().trim());
        shift.setStartTime(request.getStartTime());
        shift.setEndTime(request.getEndTime());
        shift.setSlotMinutes(request.getSlotMinutes());
        shift.setDaysOfWeek(GymShift.toDaysOfWeek(toDays(request.getDaysOfWeek())));
        if (request.getActive() != null) {
            shift.setActive(request.getActive());
        }

        validate(shift, branchId, shiftId);
        GymShift saved = shiftRepository.save(shift);
        log.info("Gym {} updated shift {} on branch {}", gymUsername, shiftId, branchId);
        return GymShiftResponse.of(saved, slotResolver.slotsOf(saved).size());
    }

    @Override
    @Transactional
    public void delete(String gymUsername, Long branchId, Long shiftId) {
        requireOwnedBranch(gymUsername, branchId);
        GymShift shift = requireShiftOfBranch(shiftId, branchId);
        assertNoBookedSessions(shift, "Không thể xoá ca");

        // Dọn phân ca trước: FK từ pt_shift_assignments chặn xoá ca, và để lại
        // dòng phân ca trỏ vào ca đã xoá thì lưới roster vỡ.
        List<PtShiftAssignment> assignments = shiftAssignmentRepository.findByGymShift_Id(shiftId);
        if (!assignments.isEmpty()) {
            shiftAssignmentRepository.deleteAll(assignments);
        }
        shiftRepository.delete(shift);
        log.info("Gym {} deleted shift {} (branch {}, {} roster row(s) removed)",
                gymUsername, shiftId, branchId, assignments.size());
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> shiftsOutsideOperatingHours(Long branchId) {
        Map<DayOfWeek, OperatingHour> hours = hoursByDay(branchId);
        List<String> problems = new ArrayList<>();
        for (GymShift shift : shiftRepository.findByGymBranch_IdAndActiveTrueOrderByStartTimeAsc(branchId)) {
            problems.addAll(operatingHourProblems(shift, hours));
        }
        return problems;
    }

    // ---------- validate ----------

    private void validate(GymShift shift, Long branchId, Long excludeShiftId) {
        List<String> reasons = new ArrayList<>();

        // Edge case §7.5 — ca vắt nửa đêm bị chặn: work_date là MỘT ngày, ca
        // 22:00-01:00 sẽ đẩy slot sang hôm sau và lệch TrainingSession.sessionDate.
        if (!shift.getStartTime().isBefore(shift.getEndTime())) {
            reasons.add("giờ kết thúc phải sau giờ bắt đầu (ca không được vắt qua nửa đêm — "
                    + "muốn ca đêm thì khai thành hai ca)");
        } else {
            long minutes = Duration.between(shift.getStartTime(), shift.getEndTime()).toMinutes();
            if (minutes % shift.getSlotMinutes() != 0) {
                reasons.add("độ dài ca (" + minutes + " phút) phải chia hết cho slot "
                        + shift.getSlotMinutes() + " phút, nếu không slot cuối bị cụt");
            }
        }
        if (shift.daysOfWeekSet().isEmpty()) {
            reasons.add("phải chọn ít nhất một thứ trong tuần");
        }
        if (shiftRepository.existsByGymBranch_IdAndNameAndIdNot(
                branchId, shift.getName(), excludeShiftId == null ? -1L : excludeShiftId)) {
            reasons.add("chi nhánh đã có ca tên " + shift.getName());
        }

        reasons.addAll(operatingHourProblems(shift, hoursByDay(branchId)));
        reasons.addAll(overlapProblems(shift, branchId, excludeShiftId));

        if (!reasons.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Ca không hợp lệ: " + String.join("; ", reasons));
        }
    }

    /** Ca phải nằm trong giờ mở cửa của MỌI thứ nó áp dụng — không im lặng bỏ qua. */
    private List<String> operatingHourProblems(GymShift shift, Map<DayOfWeek, OperatingHour> hours) {
        List<String> problems = new ArrayList<>();
        for (DayOfWeek day : shift.daysOfWeekSet()) {
            OperatingHour hour = hours.get(day);
            if (hour == null) {
                problems.add("chi nhánh chưa khai giờ mở cửa cho " + vi(day));
                continue;
            }
            if (hour.isClosed()) {
                problems.add("chi nhánh đóng cửa " + vi(day));
                continue;
            }
            if (hour.getOpenTime() == null || hour.getCloseTime() == null) {
                problems.add("giờ mở cửa " + vi(day) + " chưa đầy đủ");
                continue;
            }
            if (shift.getStartTime().isBefore(hour.getOpenTime())
                    || shift.getEndTime().isAfter(hour.getCloseTime())) {
                problems.add("ca " + shift.getName() + " (" + shift.getStartTime() + "-"
                        + shift.getEndTime() + ") nằm ngoài giờ mở cửa " + vi(day) + " ("
                        + hour.getOpenTime() + "-" + hour.getCloseTime() + ")");
            }
        }
        return problems;
    }

    /** Hai ca cùng chi nhánh không được chồng giờ trên cùng một thứ. */
    private List<String> overlapProblems(GymShift shift, Long branchId, Long excludeShiftId) {
        List<String> problems = new ArrayList<>();
        for (GymShift other : shiftRepository.findByGymBranch_IdAndActiveTrueOrderByStartTimeAsc(branchId)) {
            if (other.getId() != null && other.getId().equals(excludeShiftId)) {
                continue;
            }
            Set<DayOfWeek> shared = new LinkedHashSet<>(shift.daysOfWeekSet());
            shared.retainAll(other.daysOfWeekSet());
            if (!shared.isEmpty() && slotResolver.shiftsOverlap(shift, other)) {
                problems.add("chồng giờ với ca " + other.getName() + " (" + other.getStartTime()
                        + "-" + other.getEndTime() + ") vào "
                        + shared.stream().map(GymShiftServiceImpl::vi).collect(Collectors.joining("/")));
            }
        }
        return problems;
    }

    private void assertNoBookedSessions(GymShift shift, String prefix) {
        List<PtShiftAssignment> future = shiftAssignmentRepository
                .findByGymShift_IdAndWorkDateGreaterThanEqual(shift.getId(), LocalDate.now());
        List<TrainingSession> conflicts = conflictFinder.sessionsInAssignments(future);
        if (!conflicts.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    prefix + ": còn " + conflicts.size() + " buổi tập đã đặt trong ca này ("
                            + conflictFinder.describe(conflicts)
                            + "). Hãy để khách đổi PT hoặc đổi giờ trước.");
        }
    }

    // ---------- helpers ----------

    private Map<DayOfWeek, OperatingHour> hoursByDay(Long branchId) {
        return operatingHourRepository.findByGymBranch_IdOrderByDayOfWeek(branchId).stream()
                .filter(h -> h.getDayOfWeek() != null && h.getDayOfWeek() >= 1 && h.getDayOfWeek() <= 7)
                .collect(Collectors.toMap(h -> DayOfWeek.of(h.getDayOfWeek()), Function.identity(),
                        (a, b) -> a));
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

    private GymBranch requireOwnedBranch(String gymUsername, Long branchId) {
        return branchRepository.findByIdAndGymProfile_User_Username(branchId, gymUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Gym branch", branchId));
    }

    private GymShift requireShiftOfBranch(Long shiftId, Long branchId) {
        GymShift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Gym shift", shiftId));
        if (!shift.getGymBranch().getId().equals(branchId)) {
            throw new ResourceNotFoundException("Gym shift", shiftId);
        }
        return shift;
    }

    /** Nhãn thứ tiếng Việt cho thông điệp lỗi — T2..T7/CN, không phải MONDAY. */
    static String vi(DayOfWeek day) {
        return day == DayOfWeek.SUNDAY ? "CN" : "T" + (day.getValue() + 1);
    }
}
