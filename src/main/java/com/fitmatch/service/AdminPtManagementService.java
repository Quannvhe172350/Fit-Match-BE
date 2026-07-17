package com.fitmatch.service;

import com.fitmatch.dto.pt.GymPtResponse;

/**
 * UC-021: Admin can thiệp trạng thái PT khi có sự cố chất lượng/an toàn.
 */
public interface AdminPtManagementService {

    /**
     * Đình chỉ PT (mọi trạng thái trừ SUSPENDED) -> SUSPENDED + lý do, ẩn khỏi marketplace.
     * Tham số là User.id của tài khoản PT (caller là trang quản lý user),
     * KHÔNG phải PtProfile.id — hai dãy id độc lập, truyền nhầm sẽ đình chỉ sai người.
     */
    GymPtResponse suspend(Long userId, String reason, String actorUsername);

    /** Gỡ đình chỉ: SUSPENDED -> ACTIVE (Gym có thể tắt lại bằng INACTIVE nếu muốn). Tham số là User.id. */
    GymPtResponse reactivate(Long userId, String actorUsername);
}
