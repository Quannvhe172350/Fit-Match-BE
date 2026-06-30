package com.fitmatch.service.impl;

import com.fitmatch.common.enums.VerificationStatus;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.gym.GymDocumentDto;
import com.fitmatch.dto.gym.GymProfileResponse;
import com.fitmatch.entity.GymProfile;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.GymDocumentRepository;
import com.fitmatch.repository.GymProfileRepository;
import com.fitmatch.repository.UserRepository;
import com.fitmatch.service.AdminGymVerificationService;
import com.fitmatch.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminGymVerificationServiceImpl implements AdminGymVerificationService {

    private final GymProfileRepository gymProfileRepository;
    private final GymDocumentRepository gymDocumentRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<GymProfileResponse> list(VerificationStatus status, Pageable pageable) {
        VerificationStatus effective = status != null ? status : VerificationStatus.PENDING;
        return PageResponse.of(gymProfileRepository.findByVerificationStatus(effective, pageable), this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public GymProfileResponse detail(Long profileId) {
        return toResponse(requireProfile(profileId));
    }

    GymProfile requireProfile(Long profileId) {
        return gymProfileRepository.findById(profileId)
                .orElseThrow(() -> new ResourceNotFoundException("Gym profile", profileId));
    }

    GymProfileResponse toResponse(GymProfile profile) {
        return GymProfileResponse.of(profile,
                gymDocumentRepository.findByGymProfile_Id(profile.getId()).stream()
                        .map(GymDocumentDto::of).toList());
    }
}
