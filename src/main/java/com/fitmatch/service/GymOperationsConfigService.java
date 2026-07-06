package com.fitmatch.service;

import com.fitmatch.dto.gym.GymPolicyRequest;
import com.fitmatch.dto.gym.GymPolicyResponse;
import com.fitmatch.dto.gym.OperatingHourDto;
import com.fitmatch.dto.gym.UpdateOperatingHoursRequest;

import java.util.List;

/**
 * UC-017: cấu hình giờ hoạt động, sức chứa và chính sách vận hành của Gym.
 * (Sức chứa nằm trong Branch CRUD; ở đây là operating hours + policies.)
 */
public interface GymOperationsConfigService {

    /** Thay toàn bộ lịch hoạt động tuần của chi nhánh (validate trùng ngày, open < close). */
    List<OperatingHourDto> updateOperatingHours(String username, Long branchId, UpdateOperatingHoursRequest request);

    List<OperatingHourDto> getOperatingHours(String username, Long branchId);

    /** Upsert chính sách vận hành của Gym. */
    GymPolicyResponse upsertPolicy(String username, GymPolicyRequest request);

    GymPolicyResponse getPolicy(String username);
}
