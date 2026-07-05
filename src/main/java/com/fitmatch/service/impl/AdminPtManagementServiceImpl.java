package com.fitmatch.service.impl;

import com.fitmatch.common.AuditActions;
import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.PtStatus;
import com.fitmatch.dto.pt.GymPtResponse;
import com.fitmatch.entity.PtProfile;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.PtProfileRepository;
import com.fitmatch.service.AdminPtManagementService;
import com.fitmatch.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminPtManagementServiceImpl implements AdminPtManagementService {

    private final PtProfileRepository ptProfileRepository;
    private final AuditService auditService;

    @Override
    @Transactional
    public GymPtResponse suspend(Long ptId, String reason, String actorUsername) {
        PtProfile profile = requirePt(ptId);
        if (profile.getStatus() == PtStatus.SUSPENDED) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "PT is already suspended");
        }
        profile.setStatus(PtStatus.SUSPENDED);
        profile.setSuspensionReason(reason);
        profile.setActive(false);
        ptProfileRepository.save(profile);

        auditService.record(AuditActions.PT_SUSPEND, "PtProfile", ptId,
                "Suspended by " + actorUsername + ": " + reason);
        log.info("PT {} suspended by {}", ptId, actorUsername);
        return GymPtResponse.of(profile);
    }

    @Override
    @Transactional
    public GymPtResponse reactivate(Long ptId, String actorUsername) {
        PtProfile profile = requirePt(ptId);
        if (profile.getStatus() != PtStatus.SUSPENDED) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Only a SUSPENDED PT can be reactivated (current: " + profile.getStatus() + ")");
        }
        profile.setStatus(PtStatus.ACTIVE);
        profile.setSuspensionReason(null);
        profile.setActive(true);
        ptProfileRepository.save(profile);

        auditService.record(AuditActions.PT_REACTIVATE, "PtProfile", ptId,
                "Reactivated by " + actorUsername);
        log.info("PT {} reactivated by {}", ptId, actorUsername);
        return GymPtResponse.of(profile);
    }

    private PtProfile requirePt(Long ptId) {
        return ptProfileRepository.findById(ptId)
                .orElseThrow(() -> new ResourceNotFoundException("PT profile", ptId));
    }
}
