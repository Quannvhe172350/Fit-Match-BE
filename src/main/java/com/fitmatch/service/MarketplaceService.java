package com.fitmatch.service;

import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.gym.GymPublicProfileResponse;
import com.fitmatch.dto.pt.PtPublicProfileResponse;
import org.springframework.data.domain.Pageable;

public interface MarketplaceService {

    /** UC-14: tìm kiếm/lọc PT công khai (chỉ APPROVED & active). */
    PageResponse<PtPublicProfileResponse> searchPts(String keyword, String specialization,
                                                    String serviceArea, Pageable pageable);

    /** UC-14: xem chi tiết PT công khai (kèm chứng chỉ). */
    PtPublicProfileResponse getPtDetail(Long ptProfileId);

    /** UC-18: tìm kiếm/lọc Gym công khai (chỉ APPROVED & active). */
    PageResponse<GymPublicProfileResponse> searchGyms(String keyword, String city, Pageable pageable);

    /** UC-18: xem chi tiết Gym công khai. */
    GymPublicProfileResponse getGymDetail(Long gymProfileId);
}
