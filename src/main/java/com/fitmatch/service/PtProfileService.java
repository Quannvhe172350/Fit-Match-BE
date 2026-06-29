package com.fitmatch.service;

import com.fitmatch.dto.pt.PtProfileResponse;
import com.fitmatch.dto.pt.SubmitPtRegistrationRequest;
import com.fitmatch.dto.pt.UpdatePtProfileRequest;

public interface PtProfileService {

    /** UC-23: nộp hồ sơ đăng ký PT + tài liệu (status -> PENDING). */
    PtProfileResponse submitRegistration(String username, SubmitPtRegistrationRequest request);

    /** UC-24: xem hồ sơ + trạng thái xác minh của chính PT. */
    PtProfileResponse getOwnProfile(String username);

    /** UC-25: nộp lại hồ sơ xác minh (chỉ khi đang REJECTED) -> PENDING. */
    PtProfileResponse resubmitRegistration(String username, SubmitPtRegistrationRequest request);

    /** UC-26: cập nhật một phần hồ sơ & khu vực phục vụ (không đổi trạng thái xác minh). */
    PtProfileResponse updateProfile(String username, UpdatePtProfileRequest request);
}
