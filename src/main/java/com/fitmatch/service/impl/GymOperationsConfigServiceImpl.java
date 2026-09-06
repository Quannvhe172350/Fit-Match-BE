package com.fitmatch.service.impl;

import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.dto.gym.GymPolicyRequest;
import com.fitmatch.dto.gym.GymPolicyResponse;
import com.fitmatch.dto.gym.OperatingHourDto;
import com.fitmatch.dto.gym.UpdateOperatingHoursRequest;
import com.fitmatch.entity.GymBranch;
import com.fitmatch.entity.GymPolicy;
import com.fitmatch.entity.GymProfile;
import com.fitmatch.entity.OperatingHour;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.GymBranchRepository;
import com.fitmatch.repository.GymPolicyRepository;
import com.fitmatch.repository.OperatingHourRepository;
import com.fitmatch.service.GymOperationsConfigService;
import com.fitmatch.service.support.GymProfileResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GymOperationsConfigServiceImpl implements GymOperationsConfigService {

    private final OperatingHourRepository operatingHourRepository;
    private final com.fitmatch.repository.GymShiftRepository gymShiftRepository;
    private final GymPolicyRepository gymPolicyRepository;
    private final GymBranchRepository gymBranchRepository;
    private final GymProfileResolver gymProfileResolver;

    @Override
    @Transactional
    public List<OperatingHourDto> updateOperatingHours(String username, Long branchId,
                                                       UpdateOperatingHoursRequest request) {
        GymBranch branch = requireOwnedBranch(username, branchId);

        Set<Integer> seenDays = new HashSet<>();
        for (OperatingHourDto dto : request.getHours()) {
            if (!seenDays.add(dto.getDayOfWeek())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "Duplicate dayOfWeek: " + dto.getDayOfWeek());
            }
            boolean closed = Boolean.TRUE.equals(dto.getClosed());
            if (!closed) {
                if (dto.getOpenTime() == null || dto.getCloseTime() == null) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                            "openTime and closeTime are required when the day is not closed (day "
                                    + dto.getDayOfWeek() + ")");
                }
                if (!dto.getOpenTime().isBefore(dto.getCloseTime())) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                            "openTime must be before closeTime (day " + dto.getDayOfWeek() + ")");
                }
            }
        }

        // Câu 26/27: giờ mở cửa KHÔNG tham gia validate lịch tập của khách — vé có
        // giá trị cả ngày. NHƯNG từ V85 nó ràng buộc CA làm việc của PT, nên
        // (edge case §7.6) phải kiểm ngược: đổi giờ mở cửa làm ca hiện có rơi ra
        // ngoài thì chặn, kèm danh sách ca vướng. Không tự co ca — im lặng đổi
        // giờ làm của PT tệ hơn là bắt Gym sửa ca trước.
        assertShiftsStillInsideHours(branchId, request);

        // Thay toàn bộ lịch tuần (replace-all) để tránh trạng thái nửa vời.
        // P1-22: flush ngay sau delete để Hibernate không sắp xếp INSERT trước
        // DELETE (cùng khoá uk_operating_hours_branch_day) gây 500 khi cập nhật lại.
        operatingHourRepository.deleteByGymBranch_Id(branchId);
        operatingHourRepository.flush();
        List<OperatingHour> saved = operatingHourRepository.saveAll(request.getHours().stream()
                .map(dto -> OperatingHour.builder()
                        .gymBranch(branch)
                        .dayOfWeek(dto.getDayOfWeek())
                        .openTime(dto.getOpenTime())
                        .closeTime(dto.getCloseTime())
                        .closed(Boolean.TRUE.equals(dto.getClosed()))
                        .build())
                .toList());
        log.info("Gym {} updated operating hours of branch {} ({} days)", username, branchId, saved.size());
        return saved.stream().map(OperatingHourDto::of).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OperatingHourDto> getOperatingHours(String username, Long branchId) {
        requireOwnedBranch(username, branchId);
        return operatingHourRepository.findByGymBranch_IdOrderByDayOfWeek(branchId).stream()
                .map(OperatingHourDto::of).toList();
    }

    @Override
    @Transactional
    public GymPolicyResponse upsertPolicy(String username, GymPolicyRequest request) {
        GymProfile gym = gymProfileResolver.requireApprovedGym(username);
        GymPolicy policy = gymPolicyRepository.findByGymProfile_Id(gym.getId())
                .orElseGet(() -> GymPolicy.builder().gymProfile(gym).build());
        policy.setBookingPolicy(request.getBookingPolicy());
        policy.setCancellationPolicy(request.getCancellationPolicy());
        policy.setNoShowPolicy(request.getNoShowPolicy());
        policy.setHouseRules(request.getHouseRules());
        /*
         * Ba mốc huỷ: chỉ ghi đè khi form GỬI LÊN. Bốn ô văn bản ở trên gán thẳng
         * vì null ở đó nghĩa là "xoá nội dung", còn null ở đây nghĩa là "form đời
         * cũ không có ô này" — gán thẳng sẽ âm thầm xoá chính sách tiền của gym.
         */
        if (request.getCancelFullRefundHours() != null) {
            policy.setCancelFullRefundHours(request.getCancelFullRefundHours());
        }
        if (request.getCancelPartialRefundHours() != null) {
            policy.setCancelPartialRefundHours(request.getCancelPartialRefundHours());
        }
        if (request.getCancelPartialRefundPercent() != null) {
            policy.setCancelPartialRefundPercent(request.getCancelPartialRefundPercent());
        }
        assertCancelTiersOrdered(policy);
        policy = gymPolicyRepository.save(policy);
        log.info("Gym {} upserted policies", username);
        return GymPolicyResponse.of(policy);
    }

    @Override
    @Transactional(readOnly = true)
    public GymPolicyResponse getPolicy(String username) {
        GymPolicy policy = gymPolicyRepository.findByGymProfile_User_Username(username)
                .orElseThrow(() -> new ResourceNotFoundException("Gym policy for user", username));
        return GymPolicyResponse.of(policy);
    }

    /**
     * Mốc hoàn 100% phải SỚM HƠN (số giờ lớn hơn hoặc bằng) mốc hoàn một phần.
     * Đảo ngược thì bậc "hoàn một phần" không bao giờ với tới được — huỷ 6h
     * trước mà hoàn 100%, huỷ 20h trước lại hoàn 50%: một chính sách thưởng cho
     * người báo muộn, và không ai cố ý cấu hình như vậy.
     */
    private void assertCancelTiersOrdered(GymPolicy policy) {
        Integer full = policy.getCancelFullRefundHours();
        Integer partial = policy.getCancelPartialRefundHours();
        if (full != null && partial != null && full < partial) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Mốc hoàn 100% (" + full + "h) phải sớm hơn hoặc bằng mốc hoàn một phần ("
                            + partial + "h)");
        }
    }

    private GymBranch requireOwnedBranch(String username, Long branchId) {
        return gymBranchRepository.findByIdAndGymProfile_User_Username(branchId, username)
                .orElseThrow(() -> new ResourceNotFoundException("Gym branch", branchId));
    }

    /**
     * Ca của chi nhánh phải vẫn nằm trong giờ mở cửa MỚI. Kiểm trước khi ghi để
     * không rơi vào trạng thái nửa vời: giờ đã đổi mà ca thì sai.
     */
    private void assertShiftsStillInsideHours(Long branchId, UpdateOperatingHoursRequest request) {
        java.util.Map<java.time.DayOfWeek, OperatingHourDto> incoming = new java.util.HashMap<>();
        for (OperatingHourDto dto : request.getHours()) {
            if (dto.getDayOfWeek() != null && dto.getDayOfWeek() >= 1 && dto.getDayOfWeek() <= 7) {
                incoming.put(java.time.DayOfWeek.of(dto.getDayOfWeek()), dto);
            }
        }
        List<String> problems = new java.util.ArrayList<>();
        for (com.fitmatch.entity.GymShift shift
                : gymShiftRepository.findByGymBranch_IdAndActiveTrueOrderByStartTimeAsc(branchId)) {
            for (java.time.DayOfWeek day : shift.daysOfWeekSet()) {
                OperatingHourDto dto = incoming.get(day);
                String label = day == java.time.DayOfWeek.SUNDAY ? "CN" : "T" + (day.getValue() + 1);
                if (dto == null) {
                    problems.add("ca " + shift.getName() + " áp dụng " + label
                            + " nhưng lịch mới không khai ngày này");
                } else if (Boolean.TRUE.equals(dto.getClosed())) {
                    problems.add("ca " + shift.getName() + " áp dụng " + label
                            + " nhưng lịch mới đóng cửa ngày này");
                } else if (dto.getOpenTime() != null && dto.getCloseTime() != null
                        && (shift.getStartTime().isBefore(dto.getOpenTime())
                        || shift.getEndTime().isAfter(dto.getCloseTime()))) {
                    problems.add("ca " + shift.getName() + " (" + shift.getStartTime() + "-"
                            + shift.getEndTime() + ") nằm ngoài giờ mở cửa mới " + label + " ("
                            + dto.getOpenTime() + "-" + dto.getCloseTime() + ")");
                }
            }
        }
        if (!problems.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Không thể đổi giờ mở cửa: " + String.join("; ", problems)
                            + ". Sửa hoặc tắt các ca này trước.");
        }
    }
}
