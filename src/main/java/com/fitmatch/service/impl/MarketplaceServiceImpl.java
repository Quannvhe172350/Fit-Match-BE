package com.fitmatch.service.impl;

import com.fitmatch.common.enums.CatalogStatus;
import com.fitmatch.common.enums.PtStatus;
import com.fitmatch.common.enums.VerificationStatus;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.gym.BranchResponse;
import com.fitmatch.dto.gym.GymMediaResponse;
import com.fitmatch.dto.gym.GymPublicProfileResponse;
import com.fitmatch.dto.gym.GymServiceResponse;
import com.fitmatch.dto.gym.OperatingHourDto;
import com.fitmatch.dto.gym.TrainingPackageResponse;
import com.fitmatch.dto.pt.CertificationResponse;
import com.fitmatch.dto.pt.PtPublicProfileResponse;
import com.fitmatch.entity.GymProfile;
import com.fitmatch.entity.PtProfile;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.GymBranchRepository;
import com.fitmatch.repository.GymMediaRepository;
import com.fitmatch.repository.GymProfileRepository;
import com.fitmatch.repository.GymServiceRepository;
import com.fitmatch.repository.OperatingHourRepository;
import com.fitmatch.repository.PtCertificationRepository;
import com.fitmatch.repository.PtProfileRepository;
import com.fitmatch.repository.TrainingPackageRepository;
import com.fitmatch.repository.spec.GymProfileSpecifications;
import com.fitmatch.repository.spec.PtProfileSpecifications;
import com.fitmatch.service.MarketplaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MarketplaceServiceImpl implements MarketplaceService {

    private final PtProfileRepository ptProfileRepository;
    private final PtCertificationRepository ptCertificationRepository;
    private final GymProfileRepository gymProfileRepository;
    private final GymBranchRepository gymBranchRepository;
    private final GymServiceRepository gymServiceRepository;
    private final TrainingPackageRepository trainingPackageRepository;
    private final GymMediaRepository gymMediaRepository;
    private final OperatingHourRepository operatingHourRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PtPublicProfileResponse> searchPts(String keyword, String specialization,
                                                           String serviceArea, Pageable pageable) {
        Specification<PtProfile> spec = Specification.where(PtProfileSpecifications.visibleOnMarketplace())
                .and(PtProfileSpecifications.keyword(keyword))
                .and(PtProfileSpecifications.specialization(specialization))
                .and(PtProfileSpecifications.serviceArea(serviceArea));
        // Danh sách: không kèm chứng chỉ để tránh N+1; chứng chỉ chỉ trả ở detail.
        return PageResponse.of(ptProfileRepository.findAll(spec, pageable),
                p -> PtPublicProfileResponse.of(p, List.of()));
    }

    @Override
    @Transactional(readOnly = true)
    public PtPublicProfileResponse getPtDetail(Long ptProfileId) {
        // UC-021: PT hiển thị khi ACTIVE và Gym chịu trách nhiệm APPROVED + đang hiển thị.
        PtProfile profile = ptProfileRepository
                .findByIdAndStatusAndGymProfile_VerificationStatusAndGymProfile_ActiveTrue(
                        ptProfileId, PtStatus.ACTIVE, VerificationStatus.APPROVED)
                .orElseThrow(() -> new ResourceNotFoundException("PT profile", ptProfileId));
        List<CertificationResponse> certs = ptCertificationRepository.findByPtProfile_Id(ptProfileId).stream()
                .map(CertificationResponse::of).toList();
        return PtPublicProfileResponse.of(profile, certs);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<GymPublicProfileResponse> searchGyms(String keyword, String city, Pageable pageable) {
        Specification<GymProfile> spec = Specification.where(GymProfileSpecifications.visibleOnMarketplace())
                .and(GymProfileSpecifications.keyword(keyword))
                .and(GymProfileSpecifications.city(city));
        return PageResponse.of(gymProfileRepository.findAll(spec, pageable), GymPublicProfileResponse::of);
    }

    @Override
    @Transactional(readOnly = true)
    public GymPublicProfileResponse getGymDetail(Long gymProfileId) {
        return GymPublicProfileResponse.of(requireVisibleGym(gymProfileId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<BranchResponse> listGymBranches(Long gymProfileId) {
        requireVisibleGym(gymProfileId);
        return gymBranchRepository.findByGymProfile_IdAndActiveTrue(gymProfileId).stream()
                .map(b -> BranchResponse.of(b,
                        operatingHourRepository.findByGymBranch_IdOrderByDayOfWeek(b.getId()).stream()
                                .map(OperatingHourDto::of).toList()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<GymServiceResponse> listGymServices(Long gymProfileId) {
        requireVisibleGym(gymProfileId);
        return gymServiceRepository.findByGymProfile_IdAndStatus(gymProfileId, CatalogStatus.PUBLISHED)
                .stream().map(GymServiceResponse::of).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrainingPackageResponse> listGymPackages(Long gymProfileId) {
        requireVisibleGym(gymProfileId);
        return trainingPackageRepository.findByGymProfile_IdAndStatus(gymProfileId, CatalogStatus.PUBLISHED)
                .stream().map(TrainingPackageResponse::of).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<GymMediaResponse> listGymMedia(Long gymProfileId) {
        requireVisibleGym(gymProfileId);
        return gymMediaRepository.findByGymProfile_Id(gymProfileId)
                .stream().map(GymMediaResponse::of).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PtPublicProfileResponse> listGymPts(Long gymProfileId, Pageable pageable) {
        requireVisibleGym(gymProfileId);
        // Danh sách: không kèm chứng chỉ (tránh N+1); chứng chỉ trả ở PT detail.
        return PageResponse.of(
                ptProfileRepository.findByGymProfile_IdAndStatus(gymProfileId, PtStatus.ACTIVE, pageable),
                p -> PtPublicProfileResponse.of(p, List.of()));
    }

    /** Gym chỉ public khi APPROVED + đang hiển thị (UC-018) — 404 nếu không. */
    private GymProfile requireVisibleGym(Long gymProfileId) {
        return gymProfileRepository
                .findByIdAndVerificationStatusAndActiveTrue(gymProfileId, VerificationStatus.APPROVED)
                .orElseThrow(() -> new ResourceNotFoundException("Gym profile", gymProfileId));
    }
}
