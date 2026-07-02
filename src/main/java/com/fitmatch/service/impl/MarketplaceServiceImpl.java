package com.fitmatch.service.impl;

import com.fitmatch.common.enums.VerificationStatus;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.pt.CertificationResponse;
import com.fitmatch.dto.pt.PtPublicProfileResponse;
import com.fitmatch.entity.PtProfile;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.PtCertificationRepository;
import com.fitmatch.repository.PtProfileRepository;
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
        PtProfile profile = ptProfileRepository
                .findByIdAndVerificationStatusAndActiveTrue(ptProfileId, VerificationStatus.APPROVED)
                .orElseThrow(() -> new ResourceNotFoundException("PT profile", ptProfileId));
        List<CertificationResponse> certs = ptCertificationRepository.findByPtProfile_Id(ptProfileId).stream()
                .map(CertificationResponse::of).toList();
        return PtPublicProfileResponse.of(profile, certs);
    }
}
