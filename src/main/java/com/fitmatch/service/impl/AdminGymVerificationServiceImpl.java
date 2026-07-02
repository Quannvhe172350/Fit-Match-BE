package com.fitmatch.service.impl;

import com.fitmatch.common.AuditActions;
import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.Role;
import com.fitmatch.common.enums.VerificationStatus;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.gym.GymDocumentDto;
import com.fitmatch.dto.gym.GymProfileResponse;
import com.fitmatch.entity.GymProfile;
import com.fitmatch.entity.User;
import com.fitmatch.exception.BusinessException;
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

    @Override
    @Transactional
    public GymProfileResponse approve(Long profileId, String actorUsername) {
        GymProfile profile = requirePending(profileId);
        profile.setVerificationStatus(VerificationStatus.APPROVED);
        profile.setRejectionReason(null);
        profile.setActive(true);
        gymProfileRepository.save(profile);

        User user = profile.getUser();
        if (user.getRole() != Role.ROLE_GYM_OPERATOR) {
            user.setRole(Role.ROLE_GYM_OPERATOR);
            userRepository.save(user);
        }

        auditService.record(AuditActions.GYM_VERIFY_APPROVE, "GymProfile", profileId,
                "Approved by " + actorUsername + ", user " + user.getUsername() + " promoted to ROLE_GYM_OPERATOR");
        log.info("Gym verification {} approved by {}", profileId, actorUsername);
        return toResponse(profile);
    }

    @Override
    @Transactional
    public GymProfileResponse reject(Long profileId, String reason, String actorUsername) {
        GymProfile profile = requirePending(profileId);
        profile.setVerificationStatus(VerificationStatus.REJECTED);
        profile.setRejectionReason(reason);
        profile.setActive(false);
        gymProfileRepository.save(profile);

        auditService.record(AuditActions.GYM_VERIFY_REJECT, "GymProfile", profileId,
                "Rejected by " + actorUsername + ": " + reason);
        log.info("Gym verification {} rejected by {}", profileId, actorUsername);
        return toResponse(profile);
    }

    @Override
    @Transactional
    public GymProfileResponse requestInfo(Long profileId, String note, String actorUsername) {
        GymProfile profile = requirePending(profileId);
        profile.setVerificationStatus(VerificationStatus.REQUIRES_INFO);
        profile.setReviewNote(note);
        gymProfileRepository.save(profile);

        auditService.record(AuditActions.GYM_VERIFY_REQUEST_INFO, "GymProfile", profileId,
                "Additional info requested by " + actorUsername + ": " + note);
        log.info("Gym verification {} returned for additional info by {}", profileId, actorUsername);
        return toResponse(profile);
    }

    private GymProfile requirePending(Long profileId) {
        GymProfile profile = requireProfile(profileId);
        if (profile.getVerificationStatus() != VerificationStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Only a PENDING request can be processed (current: " + profile.getVerificationStatus() + ")");
        }
        return profile;
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
