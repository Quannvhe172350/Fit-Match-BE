package com.fitmatch.service;

import com.fitmatch.dto.pt.PtAssignmentRequest;
import com.fitmatch.dto.pt.PtAssignmentResponse;

import java.util.List;

/**
 * UC-022: Gym gán PT vào chi nhánh / dịch vụ / gói tập để đưa PT vào flow booking.
 */
public interface PtAssignmentService {

    /** Tạo liên kết PT + đúng một đích (branch/service/package thuộc cùng Gym). */
    PtAssignmentResponse assign(String gymUsername, Long ptId, PtAssignmentRequest request);

    void remove(String gymUsername, Long ptId, Long assignmentId);

    List<PtAssignmentResponse> list(String gymUsername, Long ptId);
}
