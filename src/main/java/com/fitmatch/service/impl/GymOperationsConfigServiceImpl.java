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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class GymOperationsConfigServiceImpl implements GymOperationsConfigService {

    private final OperatingHourRepository operatingHourRepository;
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

        // Thay toàn bộ lịch tuần (replace-all) để tránh trạng thái nửa vời.
        operatingHourRepository.deleteByGymBranch_Id(branchId);
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

    private GymBranch requireOwnedBranch(String username, Long branchId) {
        return gymBranchRepository.findByIdAndGymProfile_User_Username(branchId, username)
                .orElseThrow(() -> new ResourceNotFoundException("Gym branch", branchId));
    }
}
