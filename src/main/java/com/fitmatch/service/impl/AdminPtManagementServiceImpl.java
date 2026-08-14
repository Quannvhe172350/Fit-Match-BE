package com.fitmatch.service.impl;

import com.fitmatch.common.AuditActions;
import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.PtStatus;
import com.fitmatch.dto.pt.GymPtResponse;
import com.fitmatch.entity.PtProfile;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.TrainingSessionRepository;
import com.fitmatch.repository.PtProfileRepository;
import com.fitmatch.service.AdminPtManagementService;
import com.fitmatch.service.AuditService;
import com.fitmatch.service.support.NotificationDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminPtManagementServiceImpl implements AdminPtManagementService {

    private final PtProfileRepository ptProfileRepository;
    private final AuditService auditService;
    private final TrainingSessionRepository trainingSessionRepository;
    private final NotificationDispatcher notificationDispatcher;

    @Override
    @Transactional
    public GymPtResponse suspend(Long userId, String reason, String actorUsername) {
        // P0-3 (audit 2026-07-17): tra theo User.id — caller là trang quản lý user;
        // trước đây tra PtProfile.id khiến admin đình chỉ nhầm PT khác (2 dãy id độc lập).
        PtProfile profile = requirePtByUser(userId);
        if (profile.getStatus() == PtStatus.SUSPENDED) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "PT is already suspended");
        }
        profile.setStatus(PtStatus.SUSPENDED);
        profile.setSuspensionReason(reason);
        profile.setActive(false);
        ptProfileRepository.save(profile);

        // P1-16: đình chỉ là hành động an toàn -> KHÔNG chặn (khác deactivate của
        // gym), nhưng phải báo khách của các buổi tập tương lai để họ đổi PT —
        // tránh "PT bị đình chỉ vì an toàn vẫn phục vụ buổi đã đặt".
        List<com.fitmatch.entity.TrainingSession> affected = trainingSessionRepository
                .findByPtProfile_IdAndStatusAndSessionDateGreaterThanEqual(
                        profile.getId(), com.fitmatch.common.enums.SessionStatus.SCHEDULED,
                        java.time.LocalDate.now());
        for (com.fitmatch.entity.TrainingSession s : affected) {
            notificationDispatcher.ptSuspendedAffectsSession(s, reason);
        }

        auditService.record(AuditActions.PT_SUSPEND, "PtProfile", profile.getId(),
                "Suspended by " + actorUsername + ": " + reason
                        + " (" + affected.size() + " buổi tập tương lai đã được báo)");
        log.info("PT profile {} (user {}) suspended by {} ({} upcoming sessions notified)",
                profile.getId(), userId, actorUsername, affected.size());
        return GymPtResponse.of(profile);
    }

    @Override
    @Transactional
    public GymPtResponse reactivate(Long userId, String actorUsername) {
        PtProfile profile = requirePtByUser(userId);
        if (profile.getStatus() != PtStatus.SUSPENDED) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Only a SUSPENDED PT can be reactivated (current: " + profile.getStatus() + ")");
        }
        profile.setStatus(PtStatus.ACTIVE);
        profile.setSuspensionReason(null);
        profile.setActive(true);
        ptProfileRepository.save(profile);

        auditService.record(AuditActions.PT_REACTIVATE, "PtProfile", profile.getId(),
                "Reactivated by " + actorUsername);
        log.info("PT profile {} (user {}) reactivated by {}", profile.getId(), userId, actorUsername);
        return GymPtResponse.of(profile);
    }

    private PtProfile requirePtByUser(Long userId) {
        return ptProfileRepository.findByUser_Id(userId)
                .orElseThrow(() -> new ResourceNotFoundException("PT profile for user", userId));
    }
}
