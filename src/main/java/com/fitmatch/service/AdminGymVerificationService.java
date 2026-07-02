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

    /** UC-46: duyệt — chỉ khi PENDING -> APPROVED + active + nâng role ROLE_GYM_OPERATOR. */
    GymProfileResponse approve(Long profileId, String actorUsername);

    /** UC-46: từ chối — chỉ khi PENDING -> REJECTED + lý do. */
    GymProfileResponse reject(Long profileId, String reason, String actorUsername);

    /** UC-013: yêu cầu bổ sung hồ sơ — chỉ khi PENDING -> REQUIRES_INFO + ghi chú review. */
    GymProfileResponse requestInfo(Long profileId, String note, String actorUsername);
}
