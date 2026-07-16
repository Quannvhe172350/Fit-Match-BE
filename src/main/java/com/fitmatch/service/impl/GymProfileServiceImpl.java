package com.fitmatch.service.impl;

import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.VerificationStatus;
import com.fitmatch.dto.gym.GymDocumentDto;
import com.fitmatch.dto.gym.GymProfileResponse;
import com.fitmatch.dto.gym.SubmitGymRegistrationRequest;
import com.fitmatch.dto.gym.UpdateGymProfileRequest;
import com.fitmatch.entity.GymDocument;
import com.fitmatch.entity.GymProfile;
import com.fitmatch.entity.User;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.GymDocumentRepository;
import com.fitmatch.repository.GymProfileRepository;
import com.fitmatch.repository.UserRepository;
import com.fitmatch.service.GymProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GymProfileServiceImpl implements GymProfileService {

    private final GymProfileRepository gymProfileRepository;
    private final GymDocumentRepository gymDocumentRepository;
    private final UserRepository userRepository;
    private final com.fitmatch.repository.GymBranchRepository gymBranchRepository;

    @Override
    @Transactional
    public GymProfileResponse submitRegistration(String username, SubmitGymRegistrationRequest request) {
        if (gymProfileRepository.existsByUser_Username(username)) {
            throw new BusinessException(ErrorCode.PROFILE_EXISTS,
                    "A gym profile already exists. Use update or resubmit instead.");
        }
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", username));

        GymProfile profile = gymProfileRepository.save(GymProfile.builder()
                .user(user)
                .gymName(request.getGymName())
                .description(request.getDescription())
                .address(request.getAddress())
                .city(request.getCity())
                .phone(request.getPhone())
                .verificationStatus(VerificationStatus.PENDING)
                .active(false)
                .build());

        List<GymDocument> documents = saveDocuments(profile, request);
        log.info("Gym registration submitted by {} (profile {})", username, profile.getId());
        return GymProfileResponse.of(profile, documents.stream().map(GymDocumentDto::of).toList());
    }

    @Override
    @Transactional(readOnly = true)
    public GymProfileResponse getOwnProfile(String username) {
        return toResponse(requireOwnProfile(username));
    }

    @Override
    @Transactional
    public GymProfileResponse resubmitRegistration(String username, SubmitGymRegistrationRequest request) {
        GymProfile profile = requireOwnProfile(username);
        // UC-013: hồ sơ bị REJECTED hoặc bị yêu cầu bổ sung (REQUIRES_INFO) đều được nộp lại.
        if (profile.getVerificationStatus() != VerificationStatus.REJECTED
                && profile.getVerificationStatus() != VerificationStatus.REQUIRES_INFO) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Only a REJECTED or REQUIRES_INFO application can be resubmitted (current: "
                            + profile.getVerificationStatus() + ")");
        }
        profile.setGymName(request.getGymName());
        profile.setDescription(request.getDescription());
        profile.setAddress(request.getAddress());
        profile.setCity(request.getCity());
        profile.setPhone(request.getPhone());
        profile.setVerificationStatus(VerificationStatus.PENDING);
        profile.setRejectionReason(null);
        profile.setReviewNote(null);
        gymProfileRepository.save(profile);

        gymDocumentRepository.deleteByGymProfile_Id(profile.getId());
        List<GymDocument> documents = saveDocuments(profile, request);
        log.info("Gym registration resubmitted by {} (profile {})", username, profile.getId());
        return GymProfileResponse.of(profile, documents.stream().map(GymDocumentDto::of).toList());
    }

    @Override
    @Transactional
    public GymProfileResponse updateProfile(String username, UpdateGymProfileRequest request) {
        GymProfile profile = requireOwnProfile(username);
        if (request.getGymName() != null) {
            profile.setGymName(request.getGymName());
        }
        if (request.getDescription() != null) {
            profile.setDescription(request.getDescription());
        }
        if (request.getAddress() != null) {
            profile.setAddress(request.getAddress());
        }
        if (request.getCity() != null) {
            profile.setCity(request.getCity());
        }
        if (request.getPhone() != null) {
            profile.setPhone(request.getPhone());
        }
        gymProfileRepository.save(profile);
        log.info("Gym profile updated by {}", username);
        return toResponse(profile);
    }

    @Override
    @Transactional
    public GymProfileResponse updateVisibility(String username, boolean visible) {
        GymProfile profile = requireOwnProfile(username);
        // UC-018: chỉ Gym đã được duyệt mới điều khiển việc hiển thị trên marketplace;
        // Gym bị SUSPENDED do Admin đình chỉ không thể tự bật lại.
        if (profile.getVerificationStatus() != VerificationStatus.APPROVED) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Only an APPROVED gym can change marketplace visibility (current: "
                            + profile.getVerificationStatus() + ")");
        }
        // UC-018 (P1-21): chỉ được publish khi hồ sơ "đủ điều kiện" — tối thiểu có
        // một chi nhánh đang hoạt động (để khách đặt lịch được), tránh lên
        // marketplace ở trạng thái trống.
        if (visible && gymBranchRepository.findByGymProfile_IdAndActiveTrue(profile.getId()).isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Cannot publish: the gym needs at least one active branch first");
        }
        profile.setActive(visible);
        gymProfileRepository.save(profile);
        log.info("Gym profile of {} is now {}", username, visible ? "published" : "hidden");
        return toResponse(profile);
    }

    private List<GymDocument> saveDocuments(GymProfile profile, SubmitGymRegistrationRequest request) {
        List<GymDocument> documents = request.getDocuments().stream()
                .map(d -> GymDocument.builder()
                        .gymProfile(profile)
                        .documentType(d.getDocumentType())
                        .fileUrl(d.getFileUrl())
                        .build())
                .toList();
        return gymDocumentRepository.saveAll(documents);
    }

    private GymProfile requireOwnProfile(String username) {
        return gymProfileRepository.findByUser_Username(username)
                .orElseThrow(() -> new ResourceNotFoundException("Gym profile for user", username));
    }

    private GymProfileResponse toResponse(GymProfile profile) {
        return GymProfileResponse.of(profile,
                gymDocumentRepository.findByGymProfile_Id(profile.getId()).stream()
                        .map(GymDocumentDto::of).toList());
    }
}
