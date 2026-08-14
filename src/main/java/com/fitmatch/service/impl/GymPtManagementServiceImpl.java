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
    private final com.fitmatch.repository.TrainingSessionRepository trainingSessionRepository;
    private final com.fitmatch.repository.GymBranchRepository gymBranchRepository;
    private final com.fitmatch.repository.PtAssignmentRepository ptAssignmentRepository;
    private final com.fitmatch.service.support.RatingAggregator ratingAggregator;
    private final com.fitmatch.repository.DisputeRepository disputeRepository;
    private final com.fitmatch.service.support.PtAvatarResolver ptAvatarResolver;

    /** Mọi phản hồi PT đều đi qua đây để bảng PT của gym không có ô ảnh trống. */
    private GymPtResponse withAvatar(PtProfile profile) {
        GymPtResponse response = GymPtResponse.of(profile);
        response.setAvatarUrl(ptAvatarResolver.urlOf(profile.getId()));
        return response;
    }

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
                // N-1: Gym chịu trách nhiệm email PT (do Gym nhập) -> đánh dấu đã
                // xác thực để PT đăng nhập được ngay. Nếu để false, verify-gate
                // (P1-11) chặn login mà không ai phát token cho PT.
                .emailVerified(true)
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

        // UC-019/022: gán chi nhánh ngay trong cùng transaction — PT không bao giờ
        // tồn tại ở trạng thái "chưa thuộc chi nhánh nào". Chi nhánh phải thuộc
        // chính Gym này và đang hoạt động (cùng luật với PtAssignmentServiceImpl).
        for (Long branchId : request.getBranchIds().stream().filter(java.util.Objects::nonNull).distinct().toList()) {
            var branch = gymBranchRepository.findByIdAndGymProfile_User_Username(branchId, gymUsername)
                    .orElseThrow(() -> new ResourceNotFoundException("Gym branch", branchId));
            if (!branch.isActive()) {
                throw new BusinessException(ErrorCode.INVALID_STATE,
                        "Cannot assign PT to a deactivated branch: " + branch.getName());
            }
            ptAssignmentRepository.save(com.fitmatch.entity.PtAssignment.builder()
                    .ptProfile(profile).gymBranch(branch).active(true).build());
        }

        auditService.record(AuditActions.PT_CREATED_BY_GYM, "PtProfile", profile.getId(),
                "PT " + ptUser.getUsername() + " created by gym " + gymUsername);
        log.info("PT {} created under gym {} (profile {})", ptUser.getUsername(), gymUsername, profile.getId());
        return withAvatar(profile);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<GymPtResponse> list(String gymUsername, Pageable pageable) {
        GymProfile gym = gymProfileResolver.requireApprovedGym(gymUsername);
        var page = ptProfileRepository.findByGymProfile_Id(gym.getId(), pageable);
        var avatars = ptAvatarResolver.urlsOf(
                page.getContent().stream().map(PtProfile::getId).toList());
        return PageResponse.of(page, p -> {
            GymPtResponse response = GymPtResponse.of(p);
            response.setAvatarUrl(avatars.get(p.getId()));
            return response;
        });
    }

    @Override
    @Transactional(readOnly = true)
    public GymPtResponse detail(String gymUsername, Long ptId) {
        return withAvatar(requireOwnedPt(gymUsername, ptId));
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
        // Số điện thoại nằm ở User chứ không ở PtProfile: Gym tạo tài khoản PT nên
        // cũng là bên sửa liên hệ khi PT đổi số. Email/username là định danh đăng
        // nhập nên KHÔNG sửa ở đây.
        if (request.getPhone() != null && profile.getUser() != null) {
            profile.getUser().setPhone(request.getPhone().isBlank() ? null : request.getPhone());
            userRepository.save(profile.getUser());
        }
        ptProfileRepository.save(profile);
        log.info("PT profile {} updated by gym {}", ptId, gymUsername);
        return withAvatar(profile);
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
        // P1-16: không cho tắt PT khi còn buổi tập tương lai — khách đã chọn PT
        // này cho ngày cụ thể, phải để họ đổi PT trước.
        if (status == PtStatus.INACTIVE) {
            long future = trainingSessionRepository
                    .countByPtProfile_IdAndStatusAndSessionDateGreaterThanEqual(
                            ptId, com.fitmatch.common.enums.SessionStatus.SCHEDULED,
                            java.time.LocalDate.now());
            if (future > 0) {
                throw new BusinessException(ErrorCode.INVALID_STATE,
                        "Không thể tắt PT: còn " + future + " buổi tập đã đặt. "
                                + "Hãy để khách đổi PT trước.");
            }
        }
        profile.setStatus(status);
        profile.setActive(status == PtStatus.ACTIVE);
        ptProfileRepository.save(profile);

        auditService.record(AuditActions.PT_STATUS_CHANGE, "PtProfile", ptId,
                "Status set to " + status + " by gym " + gymUsername);
        log.info("PT {} status set to {} by gym {}", ptId, status, gymUsername);
        return withAvatar(profile);
    }

    @Override
    @Transactional(readOnly = true)
    public com.fitmatch.dto.pt.PtPerformanceResponse getPerformance(String gymUsername, Long ptId) {
        PtProfile pt = requireOwnedPt(gymUsername, ptId);
        var rating = ratingAggregator.forPt(ptId);
        return new com.fitmatch.dto.pt.PtPerformanceResponse(
                pt.getId(), pt.getDisplayName(),
                rating.average(), rating.count(),
                // Câu 9: không còn NO_SHOW — buổi tiêu theo ngày bất kể khách có mặt.
                // Cột thứ ba giữ 0 để client cũ không vỡ khi đọc.
                trainingSessionRepository.countByPtProfile_IdAndStatus(ptId, com.fitmatch.common.enums.SessionStatus.DONE),
                trainingSessionRepository.countByPtProfile_IdAndStatus(ptId, com.fitmatch.common.enums.SessionStatus.CANCELLED),
                0L,
                disputeRepository.countBySession_PtProfile_Id(ptId));
    }

    /** PT phải thuộc Gym của operator đang đăng nhập (chống IDOR) và Gym phải còn APPROVED. */
    PtProfile requireOwnedPt(String gymUsername, Long ptId) {
        PtProfile pt = ptProfileRepository.findByIdAndGymProfile_User_Username(ptId, gymUsername)
                .orElseThrow(() -> new ResourceNotFoundException("PT profile", ptId));
        // P1-1.2: Gym bị đình chỉ (SUSPENDED) / chưa duyệt không được quản lý PT. null-safe:
        // prod luôn có gymProfile (FK optional=false), null chỉ xảy ra trong mock test.
        if (pt.getGymProfile() != null
                && pt.getGymProfile().getVerificationStatus() != com.fitmatch.common.enums.VerificationStatus.APPROVED) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Gym must be APPROVED to manage its trainers (current: "
                            + pt.getGymProfile().getVerificationStatus() + ")");
        }
        return pt;
    }
}
