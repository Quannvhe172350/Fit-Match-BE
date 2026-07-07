package com.fitmatch.service;

import com.fitmatch.dto.pt.AvailabilitySlotDto;
import com.fitmatch.dto.pt.UpdateAvailabilityRequest;

import java.util.List;

/**
 * UC-028: lịch rảnh lặp hàng tuần của PT. Gym quản lý cho PT của mình;
 * PT tự cập nhật lịch của chính mình trong khuôn khổ Gym (không khi bị SUSPENDED).
 */
public interface PtAvailabilityService {

    /** Gym thay toàn bộ lịch rảnh tuần của PT thuộc Gym. */
    List<AvailabilitySlotDto> updateForGym(String gymUsername, Long ptId, UpdateAvailabilityRequest request);

    List<AvailabilitySlotDto> getForGym(String gymUsername, Long ptId);

    /** PT tự thay lịch rảnh của mình (bị chặn khi SUSPENDED). */
    List<AvailabilitySlotDto> updateOwn(String ptUsername, UpdateAvailabilityRequest request);

    List<AvailabilitySlotDto> getOwn(String ptUsername);
}
