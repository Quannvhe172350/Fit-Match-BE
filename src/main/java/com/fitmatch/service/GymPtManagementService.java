package com.fitmatch.service;

import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.pt.CreateGymPtRequest;
import com.fitmatch.dto.pt.GymPtResponse;
import com.fitmatch.dto.pt.UpdateGymPtRequest;
import org.springframework.data.domain.Pageable;

public interface GymPtManagementService {

    /** UC-019: Gym tạo tài khoản (ROLE_PT) + hồ sơ PT dưới quyền quản lý của mình. */
    GymPtResponse createPt(String gymUsername, CreateGymPtRequest request);

    /**
     * UC-019: danh sách PT của Gym (phân trang).
     *
     * @param branchId lọc theo chi nhánh PT được phân công; null = mọi chi nhánh
     */
    PageResponse<GymPtResponse> list(String gymUsername, Long branchId, Pageable pageable);

    /** UC-019: chi tiết một PT thuộc Gym. */
    GymPtResponse detail(String gymUsername, Long ptId);

    /** UC-019: cập nhật một phần hồ sơ PT thuộc Gym. */
    GymPtResponse update(String gymUsername, Long ptId, UpdateGymPtRequest request);

    /**
     * UC-021: Gym bật/tắt PT (ACTIVE/INACTIVE). Không đổi được PT đang bị Admin
     * SUSPENDED và không tự đặt SUSPENDED.
     */
    GymPtResponse updateStatus(String gymUsername, Long ptId, com.fitmatch.common.enums.PtStatus status);

    /** UC-023 (P1-18): tổng hợp hiệu suất/chất lượng của một PT (rating, buổi, tranh chấp). */
    com.fitmatch.dto.pt.PtPerformanceResponse getPerformance(String gymUsername, Long ptId);
}
