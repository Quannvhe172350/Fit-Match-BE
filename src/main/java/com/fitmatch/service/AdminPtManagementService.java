package com.fitmatch.service;

import com.fitmatch.dto.pt.GymPtResponse;

/**
 * UC-021: Admin can thiệp trạng thái PT khi có sự cố chất lượng/an toàn.
 */
public interface AdminPtManagementService {

    /** Đình chỉ PT (mọi trạng thái trừ SUSPENDED) -> SUSPENDED + lý do, ẩn khỏi marketplace. */
    GymPtResponse suspend(Long ptId, String reason, String actorUsername);

    /** Gỡ đình chỉ: SUSPENDED -> ACTIVE (Gym có thể tắt lại bằng INACTIVE nếu muốn). */
    GymPtResponse reactivate(Long ptId, String actorUsername);
}
