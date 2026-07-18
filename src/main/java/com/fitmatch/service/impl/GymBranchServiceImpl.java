package com.fitmatch.service.impl;

import com.fitmatch.dto.gym.BranchRequest;
import com.fitmatch.dto.gym.BranchResponse;
import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.entity.GymBranch;
import com.fitmatch.entity.GymProfile;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.BookingRepository;
import com.fitmatch.repository.GymBranchRepository;
import com.fitmatch.service.GymBranchService;
import com.fitmatch.service.support.GymProfileResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GymBranchServiceImpl implements GymBranchService {

    private final GymBranchRepository branchRepository;
    private final GymProfileResolver gymProfileResolver;
    private final BookingRepository bookingRepository;
    private final com.fitmatch.repository.OperatingHourRepository operatingHourRepository;

    @Override
    @Transactional
    public BranchResponse create(String username, BranchRequest request) {
        GymProfile gym = gymProfileResolver.requireApprovedGym(username);
        GymBranch branch = branchRepository.save(GymBranch.builder()
                .gymProfile(gym)
                .name(request.getName())
                .address(request.getAddress())
                .city(request.getCity())
                .district(request.getDistrict())
                .phone(request.getPhone())
                .amenities(request.getAmenities())
                .capacity(request.getCapacity())
                .active(true)
                .build());
        // UC-017/UC-030: chi nhánh mới có sẵn giờ mặc định 06:00-22:00 cả tuần —
        // trước đây chi nhánh chưa cấu hình giờ thì mọi booking đều bị chặn
        // ("chưa cấu hình giờ hoạt động"), gym chỉnh lại sau nếu khác.
        java.util.List<com.fitmatch.entity.OperatingHour> defaults = new java.util.ArrayList<>();
        for (int day = 1; day <= 7; day++) {
            defaults.add(com.fitmatch.entity.OperatingHour.builder()
                    .gymBranch(branch)
                    .dayOfWeek(day)
                    .openTime(java.time.LocalTime.of(6, 0))
                    .closeTime(java.time.LocalTime.of(22, 0))
                    .closed(false)
                    .build());
        }
        operatingHourRepository.saveAll(defaults);
        log.info("Gym {} created branch {} (default operating hours 06:00-22:00 seeded)", username, branch.getId());
        return BranchResponse.of(branch);
    }

    @Override
    @Transactional
    public BranchResponse update(String username, Long id, BranchRequest request) {
        GymBranch branch = requireOwned(username, id);
        branch.setName(request.getName());
        branch.setAddress(request.getAddress());
        branch.setCity(request.getCity());
        branch.setDistrict(request.getDistrict());
        branch.setPhone(request.getPhone());
        branch.setAmenities(request.getAmenities());
        branch.setCapacity(request.getCapacity());
        return BranchResponse.of(branchRepository.save(branch));
    }

    @Override
    @Transactional
    public void deactivate(String username, Long id) {
        GymBranch branch = requireOwned(username, id);
        // P1-17: không cho tắt chi nhánh khi còn booking giữ chỗ trong tương lai —
        // tránh bỏ rơi khách đã đặt (dời/hủy trước).
        long future = bookingRepository.countByGymBranch_IdAndStatusInAndStartAtGreaterThan(
                id, com.fitmatch.service.support.BookingEligibilityChecker.HOLDING_STATUSES,
                java.time.LocalDateTime.now());
        if (future > 0) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Cannot deactivate branch: it has " + future
                            + " upcoming booking(s). Reschedule or cancel them first.");
        }
        branch.setActive(false);
        branchRepository.save(branch);
        log.info("Gym {} deactivated branch {}", username, id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BranchResponse> list(String username) {
        GymProfile gym = gymProfileResolver.requireApprovedGym(username);
        return branchRepository.findByGymProfile_Id(gym.getId()).stream().map(BranchResponse::of).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BranchResponse detail(String username, Long id) {
        return BranchResponse.of(requireOwned(username, id));
    }

    private GymBranch requireOwned(String username, Long id) {
        return branchRepository.findByIdAndGymProfile_User_Username(id, username)
                .orElseThrow(() -> new ResourceNotFoundException("Branch", id));
    }
}
