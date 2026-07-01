package com.fitmatch.service.support;

import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.VerificationStatus;
import com.fitmatch.entity.GymProfile;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.GymProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Helper dùng chung cho Module G (Facility/Branch/Service): xác định hồ sơ Gym
 * của operator đang đăng nhập và yêu cầu trạng thái APPROVED trước khi thao tác.
 */
@Component
@RequiredArgsConstructor
public class GymProfileResolver {

    private final GymProfileRepository gymProfileRepository;

    /** Lấy hồ sơ Gym đã APPROVED của user; 404 nếu chưa có, 409 nếu chưa được duyệt. */
    public GymProfile requireApprovedGym(String username) {
        GymProfile profile = gymProfileRepository.findByUser_Username(username)
                .orElseThrow(() -> new ResourceNotFoundException("Gym profile for user", username));
        if (profile.getVerificationStatus() != VerificationStatus.APPROVED) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Gym profile must be APPROVED before managing facilities/branches/services");
        }
        return profile;
    }
}
