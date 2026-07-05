package com.fitmatch.service.impl;

import com.fitmatch.common.AuditActions;
import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.PtStatus;
import com.fitmatch.common.enums.Role;
import com.fitmatch.common.enums.UserStatus;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.pt.CreateGymPtRequest;
import com.fitmatch.dto.pt.GymPtResponse;
import com.fitmatch.dto.pt.UpdateGymPtRequest;
import com.fitmatch.entity.GymProfile;
import com.fitmatch.entity.PtProfile;
import com.fitmatch.entity.User;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.PtProfileRepository;
import com.fitmatch.repository.UserRepository;
import com.fitmatch.service.AuditService;
import com.fitmatch.service.GymPtManagementService;
import com.fitmatch.service.support.GymProfileResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GymPtManagementServiceImpl implements GymPtManagementService {

    private final PtProfileRepository ptProfileRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final GymProfileResolver gymProfileResolver;
    private final AuditService auditService;

    @Override
    @Transactional
    public GymPtResponse createPt(String gymUsername, CreateGymPtRequest request) {
        GymProfile gym = gymProfileResolver.requireApprovedGym(gymUsername);

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS,
                    "Username '" + request.getUsername() + "' is already taken");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.EMAIL_EXISTS,
                    "Email '" + request.getEmail() + "' is already registered");
        }

        // UC-019: Gym tạo tài khoản PT — ROLE_PT ngay từ đầu, không qua platform verification.
        User ptUser = userRepository.save(User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .role(Role.ROLE_PT)
                .status(UserStatus.ACTIVE)
                .build());

        PtProfile profile = ptProfileRepository.save(PtProfile.builder()
                .user(ptUser)
                .gymProfile(gym)
                .displayName(request.getDisplayName())
                .bio(request.getBio())
                .specialization(request.getSpecialization())
                .serviceArea(request.getServiceArea())
                .experienceYears(request.getExperienceYears())
                .status(PtStatus.ACTIVE)
                .active(true)
                .build());

        auditService.record(AuditActions.PT_CREATED_BY_GYM, "PtProfile", profile.getId(),
                "PT " + ptUser.getUsername() + " created by gym " + gymUsername);
        log.info("PT {} created under gym {} (profile {})", ptUser.getUsername(), gymUsername, profile.getId());
        return GymPtResponse.of(profile);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<GymPtResponse> list(String gymUsername, Pageable pageable) {
        GymProfile gym = gymProfileResolver.requireApprovedGym(gymUsername);
        return PageResponse.of(ptProfileRepository.findByGymProfile_Id(gym.getId(), pageable), GymPtResponse::of);
    }

    @Override
    @Transactional(readOnly = true)
    public GymPtResponse detail(String gymUsername, Long ptId) {
        return GymPtResponse.of(requireOwnedPt(gymUsername, ptId));
    }

    @Override
    @Transactional
    public GymPtResponse update(String gymUsername, Long ptId, UpdateGymPtRequest request) {
        PtProfile profile = requireOwnedPt(gymUsername, ptId);
        if (request.getDisplayName() != null) {
            profile.setDisplayName(request.getDisplayName());
        }
        if (request.getBio() != null) {
            profile.setBio(request.getBio());
        }
        if (request.getSpecialization() != null) {
            profile.setSpecialization(request.getSpecialization());
        }
        if (request.getServiceArea() != null) {
            profile.setServiceArea(request.getServiceArea());
        }
        if (request.getExperienceYears() != null) {
            profile.setExperienceYears(request.getExperienceYears());
        }
        ptProfileRepository.save(profile);
        log.info("PT profile {} updated by gym {}", ptId, gymUsername);
        return GymPtResponse.of(profile);
    }

    @Override
    @Transactional
    public GymPtResponse updateStatus(String gymUsername, Long ptId, PtStatus status) {
        PtProfile profile = requireOwnedPt(gymUsername, ptId);
        if (status == PtStatus.SUSPENDED) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Suspension is an admin action; gyms can only set ACTIVE or INACTIVE");
        }
        if (profile.getStatus() == PtStatus.SUSPENDED) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "PT is suspended by platform admin; only an admin can lift the suspension");
        }
        profile.setStatus(status);
        profile.setActive(status == PtStatus.ACTIVE);
        ptProfileRepository.save(profile);

        auditService.record(AuditActions.PT_STATUS_CHANGE, "PtProfile", ptId,
                "Status set to " + status + " by gym " + gymUsername);
        log.info("PT {} status set to {} by gym {}", ptId, status, gymUsername);
        return GymPtResponse.of(profile);
    }

    /** PT phải thuộc Gym của operator đang đăng nhập (chống IDOR). */
    PtProfile requireOwnedPt(String gymUsername, Long ptId) {
        return ptProfileRepository.findByIdAndGymProfile_User_Username(ptId, gymUsername)
                .orElseThrow(() -> new ResourceNotFoundException("PT profile", ptId));
    }
}
