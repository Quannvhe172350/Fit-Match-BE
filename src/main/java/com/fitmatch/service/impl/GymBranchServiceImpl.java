package com.fitmatch.service.impl;

import com.fitmatch.dto.gym.BranchRequest;
import com.fitmatch.dto.gym.BranchResponse;
import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.entity.GymBranch;
import com.fitmatch.entity.GymProfile;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.TrainingSessionRepository;
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
    private final TrainingSessionRepository trainingSessionRepository;
    private final com.fitmatch.repository.OperatingHourRepository operatingHourRepository;
    private final com.fitmatch.service.support.AddressGeocoder addressGeocoder;

    @Override
    @Transactional
    public BranchResponse create(String username, BranchRequest request) {
        GymProfile gym = gymProfileResolver.requireApprovedGym(username);
        GymBranch branch = GymBranch.builder()
                .gymProfile(gym)
                .name(request.getName())
                .address(request.getAddress())
                .city(request.getCity())
                .district(request.getDistrict())
                .phone(request.getPhone())
                .amenities(request.getAmenities())
                .capacity(request.getCapacity())
                .active(true)
                .build();
        applyGeolocation(branch, request);
        branch = branchRepository.save(branch);
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
        String addressKey = addressKeyOf(branch);
        branch.setName(request.getName());
        branch.setAddress(request.getAddress());
        branch.setCity(request.getCity());
        branch.setDistrict(request.getDistrict());
        branch.setPhone(request.getPhone());
        branch.setAmenities(request.getAmenities());
        branch.setCapacity(request.getCapacity());
        // Chỉ geocode lại khi địa chỉ thực sự đổi, operator gửi toạ độ mới, hoặc
        // chi nhánh còn thiếu toạ độ (lần trước geocode hỏng thì lần này thử lại).
        // Nếu không, mỗi lần sửa số điện thoại lại tốn một lượt gọi Google.
        boolean addressChanged = !addressKey.equals(addressKeyOf(branch));
        if (addressChanged || request.getLatitude() != null || request.getLongitude() != null
                || branch.getLatitude() == null) {
            applyGeolocation(branch, request);
        }
        return BranchResponse.of(branchRepository.save(branch));
    }

    /** Khoá so sánh "địa chỉ có đổi không" cho {@link #update}. */
    private static String addressKeyOf(GymBranch branch) {
        return String.join("|",
                java.util.Objects.toString(branch.getAddress(), ""),
                java.util.Objects.toString(branch.getDistrict(), ""),
                java.util.Objects.toString(branch.getCity(), ""));
    }

    /**
     * UC-18 (V55): gắn toạ độ cho chi nhánh. Không phân giải được thì GIỮ NGUYÊN
     * toạ độ cũ — Google tạm lỗi/hết quota không được phép làm chi nhánh biến mất
     * khỏi kết quả tìm quanh đây.
     */
    private void applyGeolocation(GymBranch branch, BranchRequest request) {
        var pin = new com.fitmatch.service.support.AddressGeocoder.Pin(
                request.getLatitude(), request.getLongitude(),
                request.getPlaceId(), request.getFormattedAddress(), request.getPlaceProvider(),
                Boolean.TRUE.equals(request.getCoordinatesPinned()));
        var resolution = addressGeocoder.resolve(pin,
                request.getAddress(), request.getDistrict(), request.getCity());
        if (!resolution.resolved()) {
            return;
        }
        branch.setLatitude(resolution.latitude());
        branch.setLongitude(resolution.longitude());
        // Chỉ ghi khi có giá trị mới — ghim toạ độ tay không kèm metadata mà vẫn
        // set thì mỗi lần lưu là xoá trắng place_id/formatted_address đang có.
        if (resolution.placeId() != null) {
            branch.setPlaceId(resolution.placeId());
            // V65: nhãn provider luôn đi kèm id — xem javadoc ở GymProfile#placeProvider.
            branch.setPlaceProvider(resolution.placeProvider());
        }
        if (resolution.formattedAddress() != null) {
            branch.setFormattedAddress(resolution.formattedAddress());
        }
        // V59: chi nhánh chỉ LƯU độ chính xác chứ không tự gắn cờ xác minh —
        // address_verified (V58) là cờ của cả hồ sơ gym, không có ở cấp chi nhánh.
        // Admin đọc cột này khi soát hồ sơ.
        branch.setLocationType(resolution.locationType());
        branch.setCoordinatesPinned(resolution.pinnedByUser());
        branch.setGeocodedAt(resolution.geocodedAt());
    }

    @Override
    @Transactional
    public void deactivate(String username, Long id) {
        GymBranch branch = requireOwned(username, id);
        // P1-17: không cho tắt chi nhánh khi còn buổi tập đã đặt trong tương lai —
        // tránh bỏ rơi khách đã mua vé (khách phải được dời lịch trước).
        long future = trainingSessionRepository
                .countByGymBranch_IdAndStatusAndSessionDateGreaterThanEqual(
                        id, com.fitmatch.common.enums.SessionStatus.SCHEDULED, java.time.LocalDate.now());
        if (future > 0) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Không thể tắt chi nhánh: còn " + future
                            + " buổi tập đã đặt. Hãy để khách dời lịch trước.");
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
