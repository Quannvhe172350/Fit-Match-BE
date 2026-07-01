package com.fitmatch.service;

import com.fitmatch.dto.gym.FacilityRequest;
import com.fitmatch.dto.gym.FacilityResponse;

import java.util.List;

public interface GymFacilityService {

    /** UC-47: tạo cơ sở vật chất cho Gym của chính operator (yêu cầu Gym APPROVED). */
    FacilityResponse create(String username, FacilityRequest request);

    /** UC-48: cập nhật cơ sở vật chất (chủ sở hữu). */
    FacilityResponse update(String username, Long id, FacilityRequest request);

    /** UC-48: vô hiệu hoá cơ sở vật chất (active=false). */
    void deactivate(String username, Long id);

    /** UC-49: liệt kê cơ sở vật chất của chính operator. */
    List<FacilityResponse> list(String username);
}
