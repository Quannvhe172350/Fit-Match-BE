package com.fitmatch.service;

import com.fitmatch.dto.gym.GymProfileResponse;
import com.fitmatch.dto.gym.SubmitGymRegistrationRequest;
import com.fitmatch.dto.gym.UpdateGymProfileRequest;

public interface GymProfileService {

    /** UC-41: nộp hồ sơ đăng ký Gym + tài liệu (status -> PENDING). */
    GymProfileResponse submitRegistration(String username, SubmitGymRegistrationRequest request);

    /** UC-42: xem hồ sơ + trạng thái xác minh của chính Gym. */
    GymProfileResponse getOwnProfile(String username);

    /** UC-43: nộp lại hồ sơ xác minh (chỉ khi đang REJECTED) -> PENDING. */
    GymProfileResponse resubmitRegistration(String username, SubmitGymRegistrationRequest request);

    /** UC-44: cập nhật một phần hồ sơ Gym (không đổi trạng thái xác minh). */
    GymProfileResponse updateProfile(String username, UpdateGymProfileRequest request);
}
