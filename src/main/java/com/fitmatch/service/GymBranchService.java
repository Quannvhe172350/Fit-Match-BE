package com.fitmatch.service;

import com.fitmatch.dto.gym.BranchRequest;
import com.fitmatch.dto.gym.BranchResponse;

import java.util.List;

public interface GymBranchService {

    /** UC-50: tạo chi nhánh (yêu cầu Gym APPROVED). */
    BranchResponse create(String username, BranchRequest request);

    /** UC-51: cập nhật chi nhánh (chủ sở hữu). */
    BranchResponse update(String username, Long id, BranchRequest request);

    /** UC-51: vô hiệu hoá chi nhánh. */
    void deactivate(String username, Long id);

    /** UC-52: liệt kê chi nhánh của Gym mình. */
    List<BranchResponse> list(String username);

    /** UC-52: xem chi tiết một chi nhánh (chủ sở hữu). */
    BranchResponse detail(String username, Long id);
}
