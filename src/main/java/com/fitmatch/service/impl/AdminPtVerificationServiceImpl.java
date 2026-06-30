package com.fitmatch.service.impl;

import com.fitmatch.common.AuditActions;
import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.Role;
import com.fitmatch.common.enums.VerificationStatus;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.pt.PtDocumentDto;
import com.fitmatch.dto.pt.PtProfileResponse;
import com.fitmatch.entity.PtProfile;
import com.fitmatch.entity.User;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.PtDocumentRepository;
import com.fitmatch.repository.PtProfileRepository;
import com.fitmatch.repository.UserRepository;
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
    private final UserRepository userRepository;
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

    @Override
    @Transactional
    public PtProfileResponse approve(Long profileId, String actorUsername) {
        PtProfile profile = requirePending(profileId);
        profile.setVerificationStatus(VerificationStatus.APPROVED);
        profile.setRejectionReason(null);
        profile.setActive(true);
        ptProfileRepository.save(profile);

        // Nâng role tài khoản lên ROLE_PT (D-09).
        User user = profile.getUser();
        if (user.getRole() != Role.ROLE_PT) {
            user.setRole(Role.ROLE_PT);
            userRepository.save(user);
        }

        auditService.record(AuditActions.PT_VERIFY_APPROVE, "PtProfile", profileId,
                "Approved by " + actorUsername + ", user " + user.getUsername() + " promoted to ROLE_PT");
        log.info("PT verification {} approved by {}", profileId, actorUsername);
        return toResponse(profile);
    }

    @Override
    @Transactional
    public PtProfileResponse reject(Long profileId, String reason, String actorUsername) {
        PtProfile profile = requirePending(profileId);
        profile.setVerificationStatus(VerificationStatus.REJECTED);
        profile.setRejectionReason(reason);
        profile.setActive(false);
        ptProfileRepository.save(profile);

        auditService.record(AuditActions.PT_VERIFY_REJECT, "PtProfile", profileId,
                "Rejected by " + actorUsername + ": " + reason);
        log.info("PT verification {} rejected by {}", profileId, actorUsername);
        return toResponse(profile);
    }

    /** Hồ sơ phải đang ở trạng thái PENDING mới được duyệt/từ chối. */
    private PtProfile requirePending(Long profileId) {
        PtProfile profile = requireProfile(profileId);
        if (profile.getVerificationStatus() != VerificationStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Only a PENDING request can be processed (current: " + profile.getVerificationStatus() + ")");
        }
        return profile;
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
