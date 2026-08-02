package com.fitmatch.service;

import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.gym.BranchResponse;
import com.fitmatch.dto.gym.GymMediaResponse;
import com.fitmatch.dto.gym.GymPublicProfileResponse;
import com.fitmatch.dto.gym.GymServiceResponse;
import com.fitmatch.dto.gym.TrainingPackageResponse;
import com.fitmatch.dto.pt.PtPublicProfileResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface MarketplaceService {

    /** UC-14: tìm kiếm/lọc PT công khai (chỉ APPROVED & active). */
    PageResponse<PtPublicProfileResponse> searchPts(String keyword, String specialization,
                                                    String serviceArea, Pageable pageable);

    /** UC-14: xem chi tiết PT công khai (kèm chứng chỉ). */
    PtPublicProfileResponse getPtDetail(Long ptProfileId);

    /**
     * UC-18: tìm kiếm/lọc Gym công khai (chỉ APPROVED &amp; active) — keyword,
     * city/district, khoảng giá gói tập, và (V55) bán kính quanh một toạ độ.
     */
    PageResponse<GymPublicProfileResponse> searchGyms(com.fitmatch.dto.gym.GymSearchCriteria criteria,
                                                      Pageable pageable);

    /** UC-18: xem chi tiết Gym công khai. */
    GymPublicProfileResponse getGymDetail(Long gymProfileId);

    /** UC-009: chi nhánh đang hoạt động của Gym hiển thị (kèm giờ mở cửa). */
    List<BranchResponse> listGymBranches(Long gymProfileId);

    /** UC-009: dịch vụ PUBLISHED của Gym hiển thị (giá + booking rules). */
    List<GymServiceResponse> listGymServices(Long gymProfileId);

    /** UC-009: gói tập PUBLISHED của Gym hiển thị. */
    List<TrainingPackageResponse> listGymPackages(Long gymProfileId);

    /** UC-009: media công khai của Gym hiển thị. */
    List<GymMediaResponse> listGymMedia(Long gymProfileId);

    /**
     * UC-009: PT đang hoạt động của Gym hiển thị — phục vụ chọn PT khi booking.
     * Bug S2-04: {@code branchId} khác null thì chỉ trả PT được phân công cho chi
     * nhánh đó (có PT chỉ phụ trách một chi nhánh).
     */
    PageResponse<PtPublicProfileResponse> listGymPts(Long gymProfileId, Long branchId, Pageable pageable);

    /**
     * Bug S2-14: thời gian biểu tuần của PT cho trang công khai — khách biết PT
     * nhận buổi vào khung giờ nào trước khi mở wizard đặt lịch.
     */
    List<com.fitmatch.dto.pt.AvailabilitySlotDto> listPtAvailability(Long ptProfileId);
}
