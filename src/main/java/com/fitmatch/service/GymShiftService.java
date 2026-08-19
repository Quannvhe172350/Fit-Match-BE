package com.fitmatch.service;

import com.fitmatch.dto.gym.GymShiftRequest;
import com.fitmatch.dto.gym.GymShiftResponse;

import java.util.List;

/**
 * V85: Gym khai CA làm việc ở cấp chi nhánh — nguồn sự thật mới của lịch PT,
 * thay việc PT tự khai khung giờ rảnh.
 */
public interface GymShiftService {

    List<GymShiftResponse> list(String gymUsername, Long branchId);

    GymShiftResponse create(String gymUsername, Long branchId, GymShiftRequest request);

    GymShiftResponse update(String gymUsername, Long branchId, Long shiftId, GymShiftRequest request);

    /**
     * Xoá ca. Chặn nếu còn buổi tập SCHEDULED nằm trong ca — 409 kèm danh sách
     * buổi vướng để Gym xử lý trước (edge case §7.2).
     */
    void delete(String gymUsername, Long branchId, Long shiftId);

    /**
     * Edge case §7.6: sau khi Gym đổi giờ mở cửa, ca nào rơi ra ngoài. Gọi từ
     * luồng cập nhật operating hours để chặn thay đổi làm hỏng lịch có sẵn.
     */
    List<String> shiftsOutsideOperatingHours(Long branchId);
}
