package com.fitmatch.service;

import com.fitmatch.common.enums.VerificationStatus;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.gym.GymProfileResponse;
import org.springframework.data.domain.Pageable;

public interface AdminGymVerificationService {

    /** UC-45: liệt kê yêu cầu xác minh Gym theo trạng thái (mặc định PENDING). */
    PageResponse<GymProfileResponse> list(VerificationStatus status, Pageable pageable);

    /** UC-45: xem chi tiết một yêu cầu xác minh Gym (kèm tài liệu). */
    GymProfileResponse detail(Long profileId);
}
