package com.fitmatch.service;

import com.fitmatch.dto.gym.GymProfileResponse;
import com.fitmatch.dto.gym.SubmitGymRegistrationRequest;
import com.fitmatch.dto.gym.UpdateGymProfileRequest;

public interface GymProfileService {

    /** UC-41: nộp hồ sơ đăng ký Gym + tài liệu (status -> PENDING). */
    GymProfileResponse submitRegistration(String username, SubmitGymRegistrationRequest request);

    /** UC-42: xem hồ sơ + trạng thái xác minh của chính Gym. */
    GymProfileResponse getOwnProfile(String username);

    /** UC-43: nộp lại hồ sơ xác minh (chỉ khi đang REJECTED) -> PENDING. */
    GymProfileResponse resubmitRegistration(String username, SubmitGymRegistrationRequest request);

    /** UC-44: cập nhật một phần hồ sơ Gym (không đổi trạng thái xác minh). */
    GymProfileResponse updateProfile(String username, UpdateGymProfileRequest request);

    /** UC-018: hiển thị / ẩn hồ sơ Gym trên marketplace (chỉ khi APPROVED). */
    GymProfileResponse updateVisibility(String username, boolean visible);

    /** UC-012 (P1-20): danh sách tài liệu xác minh của Gym. */
    java.util.List<com.fitmatch.dto.gym.GymDocumentDto> listDocuments(String username);

    /** UC-012 (P1-20): thêm một tài liệu (chỉ khi hồ sơ chưa/đang duyệt hoặc cần bổ sung). */
    com.fitmatch.dto.gym.GymDocumentDto addDocument(String username,
                                                    com.fitmatch.dto.gym.GymDocumentDto request);

    /** UC-012 (P1-20): xoá một tài liệu (chỉ khi hồ sơ chưa/đang duyệt hoặc cần bổ sung). */
    void deleteDocument(String username, Long documentId);
}
