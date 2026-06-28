package com.fitmatch.service;

import com.fitmatch.dto.pt.PtProfileResponse;
import com.fitmatch.dto.pt.SubmitPtRegistrationRequest;

public interface PtProfileService {

    /** UC-23: nộp hồ sơ đăng ký PT + tài liệu (status -> PENDING). */
    PtProfileResponse submitRegistration(String username, SubmitPtRegistrationRequest request);
}
