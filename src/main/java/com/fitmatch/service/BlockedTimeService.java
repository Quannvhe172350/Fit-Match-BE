package com.fitmatch.service;

import com.fitmatch.dto.pt.BlockedTimeRequest;
import com.fitmatch.dto.pt.BlockedTimeResponse;

import java.util.List;

/**
 * UC-029: quản lý khoảng thời gian không nhận đặt lịch (blocked/busy time).
 */
public interface BlockedTimeService {

    /** Gym tạo blocked time cho PT hoặc chi nhánh của mình (đúng một đích). */
    BlockedTimeResponse createForGym(String gymUsername, BlockedTimeRequest request);

    /** PT tự tạo blocked time cá nhân (bị chặn khi SUSPENDED). */
    BlockedTimeResponse createForPt(String ptUsername, BlockedTimeRequest request);

    void deleteForGym(String gymUsername, Long id);

    void deleteForPt(String ptUsername, Long id);

    List<BlockedTimeResponse> listForGym(String gymUsername, Long ptId, Long branchId);

    List<BlockedTimeResponse> listForPt(String ptUsername);
}
