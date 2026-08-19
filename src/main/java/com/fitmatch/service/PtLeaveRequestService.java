package com.fitmatch.service;

import com.fitmatch.common.enums.LeaveStatus;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.gym.GymLeavePolicyDto;
import com.fitmatch.dto.pt.PtLeaveRequestCreate;
import com.fitmatch.dto.pt.PtLeaveRequestResponse;
import org.springframework.data.domain.Pageable;

/**
 * V87: đơn xin nghỉ của PT — cách duy nhất PT tác động lên lịch của mình sau
 * khi Gym trở thành chủ lịch.
 */
public interface PtLeaveRequestService {

    // ----- Phía PT -----

    PageResponse<PtLeaveRequestResponse> myRequests(String ptUsername, Pageable pageable);

    /**
     * Gửi đơn. Ném 409 khi: chồng đơn đang hiệu lực; vượt hạn mức tháng của Gym
     * (§4.2); hoặc đơn đè lên buổi đã đặt mà không đủ số giờ báo trước (§4.1,
     * cấu hình {@code pt.leave.min-lead-hours}).
     */
    PtLeaveRequestResponse submit(String ptUsername, PtLeaveRequestCreate request);

    /** PT tự huỷ đơn khi còn PENDING. */
    PtLeaveRequestResponse cancel(String ptUsername, Long requestId);

    // ----- Phía Gym -----

    PageResponse<PtLeaveRequestResponse> gymRequests(String gymUsername, LeaveStatus status,
                                                     Pageable pageable);

    long countPending(String gymUsername);

    /**
     * Duyệt đơn. Slot trong phạm vi bị vô hiệu ngay; mỗi buổi SCHEDULED bị phủ
     * sinh một dòng {@code session_pt_cancellations} và PT bị gỡ khỏi buổi để
     * KHÁCH quyết đổi PT hay nhận hoàn tiền (quyết định §4.1).
     */
    PtLeaveRequestResponse approve(String gymUsername, Long requestId);

    /** Từ chối, bắt buộc có lý do. Lịch ca của PT giữ nguyên. */
    PtLeaveRequestResponse reject(String gymUsername, Long requestId, String reason);

    GymLeavePolicyDto getPolicy(String gymUsername);

    GymLeavePolicyDto updatePolicy(String gymUsername, GymLeavePolicyDto policy);
}
