package com.fitmatch.service.impl;

import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.VerificationStatus;
import com.fitmatch.dto.pt.PtDocumentDto;
import com.fitmatch.dto.pt.PtProfileResponse;
import com.fitmatch.dto.pt.SubmitPtRegistrationRequest;
import com.fitmatch.dto.pt.UpdatePtProfileRequest;
import com.fitmatch.entity.PtDocument;
import com.fitmatch.entity.PtProfile;
import com.fitmatch.entity.User;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.PtDocumentRepository;
import com.fitmatch.repository.PtProfileRepository;
import com.fitmatch.repository.UserRepository;
import com.fitmatch.service.PtProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PtProfileServiceImpl implements PtProfileService {

    private final PtProfileRepository ptProfileRepository;
    private final PtDocumentRepository ptDocumentRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public PtProfileResponse submitRegistration(String username, SubmitPtRegistrationRequest request) {
        if (ptProfileRepository.existsByUser_Username(username)) {
            throw new BusinessException(ErrorCode.PROFILE_EXISTS,
                    "A PT profile already exists. Use update or resubmit instead.");
        }
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", username));

        PtProfile profile = ptProfileRepository.save(PtProfile.builder()
                .user(user)
                .displayName(request.getDisplayName())
                .bio(request.getBio())
                .serviceArea(request.getServiceArea())
                .specialization(request.getSpecialization())
                .experienceYears(request.getExperienceYears())
                .verificationStatus(VerificationStatus.PENDING)
                .active(false)
                .build());

        List<PtDocument> documents = request.getDocuments().stream()
                .map(d -> PtDocument.builder()
                        .ptProfile(profile)
                        .documentType(d.getDocumentType())
                        .fileUrl(d.getFileUrl())
                        .build())
                .toList();
        ptDocumentRepository.saveAll(documents);

        log.info("PT registration submitted by {} (profile {})", username, profile.getId());
        return PtProfileResponse.of(profile, documents.stream().map(PtDocumentDto::of).toList());
    }

    @Override
    @Transactional
    public PtProfileResponse updateProfile(String username, UpdatePtProfileRequest request) {
        PtProfile profile = requireOwnProfile(username);
        if (request.getDisplayName() != null) {
            profile.setDisplayName(request.getDisplayName());
        }
        if (request.getBio() != null) {
            profile.setBio(request.getBio());
        }
        if (request.getServiceArea() != null) {
            profile.setServiceArea(request.getServiceArea());
        }
        if (request.getSpecialization() != null) {
            profile.setSpecialization(request.getSpecialization());
        }
        if (request.getExperienceYears() != null) {
            profile.setExperienceYears(request.getExperienceYears());
        }
        ptProfileRepository.save(profile);
        log.info("PT profile updated by {}", username);
        return toResponse(profile);
    }

    @Override
    @Transactional(readOnly = true)
    public PtProfileResponse getOwnProfile(String username) {
        PtProfile profile = requireOwnProfile(username);
        return toResponse(profile);
    }

    @Override
    @Transactional
    public PtProfileResponse resubmitRegistration(String username, SubmitPtRegistrationRequest request) {
        PtProfile profile = requireOwnProfile(username);

        if (profile.getVerificationStatus() != VerificationStatus.REJECTED) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Only a REJECTED application can be resubmitted (current: " + profile.getVerificationStatus() + ")");
        }

        profile.setDisplayName(request.getDisplayName());
        profile.setBio(request.getBio());
        profile.setServiceArea(request.getServiceArea());
        profile.setSpecialization(request.getSpecialization());
        profile.setExperienceYears(request.getExperienceYears());
        profile.setVerificationStatus(VerificationStatus.PENDING);
        profile.setRejectionReason(null);
        ptProfileRepository.save(profile);

        // Thay thế toàn bộ tài liệu cũ bằng bộ mới.
        ptDocumentRepository.deleteByPtProfile_Id(profile.getId());
        List<PtDocument> documents = request.getDocuments().stream()
                .map(d -> PtDocument.builder()
                        .ptProfile(profile)
                        .documentType(d.getDocumentType())
                        .fileUrl(d.getFileUrl())
                        .build())
                .toList();
        ptDocumentRepository.saveAll(documents);

        log.info("PT registration resubmitted by {} (profile {})", username, profile.getId());
        return PtProfileResponse.of(profile, documents.stream().map(PtDocumentDto::of).toList());
    }

    /** Lấy hồ sơ PT của chính user, ném 404 nếu chưa nộp. */
    private PtProfile requireOwnProfile(String username) {
        return ptProfileRepository.findByUser_Username(username)
                .orElseThrow(() -> new ResourceNotFoundException("PT profile for user", username));
    }

    private PtProfileResponse toResponse(PtProfile profile) {
        List<PtDocumentDto> docs = ptDocumentRepository.findByPtProfile_Id(profile.getId()).stream()
                .map(PtDocumentDto::of).toList();
        return PtProfileResponse.of(profile, docs);
    }
}
