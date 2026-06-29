package com.fitmatch.service.impl;

import com.fitmatch.common.enums.VerificationStatus;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.pt.PtDocumentDto;
import com.fitmatch.dto.pt.PtProfileResponse;
import com.fitmatch.entity.PtProfile;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.PtDocumentRepository;
import com.fitmatch.repository.PtProfileRepository;
import com.fitmatch.service.AdminPtVerificationService;
import com.fitmatch.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminPtVerificationServiceImpl implements AdminPtVerificationService {

    private final PtProfileRepository ptProfileRepository;
    private final PtDocumentRepository ptDocumentRepository;
    private final AuditService auditService;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PtProfileResponse> list(VerificationStatus status, Pageable pageable) {
        VerificationStatus effective = status != null ? status : VerificationStatus.PENDING;
        return PageResponse.of(ptProfileRepository.findByVerificationStatus(effective, pageable), this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public PtProfileResponse detail(Long profileId) {
        return toResponse(requireProfile(profileId));
    }

    PtProfile requireProfile(Long profileId) {
        return ptProfileRepository.findById(profileId)
                .orElseThrow(() -> new ResourceNotFoundException("PT profile", profileId));
    }

    PtProfileResponse toResponse(PtProfile profile) {
        return PtProfileResponse.of(profile,
                ptDocumentRepository.findByPtProfile_Id(profile.getId()).stream()
                        .map(PtDocumentDto::of).toList());
    }
}
