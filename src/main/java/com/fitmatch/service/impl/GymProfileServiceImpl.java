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
    private final com.fitmatch.service.support.AddressGeocoder addressGeocoder;

    @Override
    @Transactional
    public GymProfileResponse submitRegistration(String username, SubmitGymRegistrationRequest request) {
        if (gymProfileRepository.existsByUser_Username(username)) {
            throw new BusinessException(ErrorCode.PROFILE_EXISTS,
                    "A gym profile already exists. Use update or resubmit instead.");
        }
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", username));

        GymProfile profile = GymProfile.builder()
                .user(user)
                .gymName(request.getGymName())
                .description(request.getDescription())
                .address(request.getAddress())
                .city(request.getCity())
                .district(request.getDistrict())
                .phone(request.getPhone())
                .verificationStatus(VerificationStatus.PENDING)
                .active(false)
                .build();
        applyGeolocation(profile, pinOf(request),
                request.getAddress(), request.getDistrict(), request.getCity());
        profile = gymProfileRepository.save(profile);

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
        profile.setDistrict(request.getDistrict());
        profile.setPhone(request.getPhone());
        applyGeolocation(profile, pinOf(request),
                request.getAddress(), request.getDistrict(), request.getCity());
        profile.setVerificationStatus(VerificationStatus.PENDING);
        profile.setRejectionReason(null);
        profile.setReviewNote(null);
        gymProfileRepository.save(profile);

        // P0-0.3: KHÔNG xóa-sạch tài liệu rồi ghi lại theo mảng của form — sẽ mất các
        // tài liệu operator vừa thêm/xóa qua DocumentManager. saveDocuments nay hợp nhất
        // (chỉ thêm URL mới). Xóa tài liệu là việc của endpoint delete riêng.
        List<GymDocument> documents = saveDocuments(profile, request);
        log.info("Gym registration resubmitted by {} (profile {})", username, profile.getId());
        return GymProfileResponse.of(profile, documents.stream().map(GymDocumentDto::of).toList());
    }

    @Override
    @Transactional
    public GymProfileResponse updateProfile(String username, UpdateGymProfileRequest request) {
        GymProfile profile = requireOwnProfile(username);
        // P1-1.2: Gym bị Admin đình chỉ (SUSPENDED) không được tự chỉnh sửa hồ sơ nghiệp vụ
        // — phải chờ gỡ đình chỉ. Các trạng thái tiền-duyệt (PENDING/REQUIRES_INFO) vẫn sửa
        // được (dùng resubmit) nên chỉ chặn SUSPENDED.
        if (profile.getVerificationStatus() == VerificationStatus.SUSPENDED) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "A suspended gym cannot edit its business profile; contact platform admin.");
        }
        String addressKey = addressKeyOf(profile);
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
        if (request.getDistrict() != null) {
            profile.setDistrict(request.getDistrict());
        }
        if (request.getPhone() != null) {
            profile.setPhone(request.getPhone());
        }
        // UC-18 (V55): chỉ geocode lại khi địa chỉ thực sự đổi, operator gửi toạ độ
        // mới, hoặc hồ sơ còn thiếu toạ độ (lần trước geocode hỏng thì lần này thử
        // lại). Nếu không, mỗi lần đổi số điện thoại lại tốn một lượt gọi Google
        // cho cùng một địa chỉ.
        boolean addressChanged = !addressKey.equals(addressKeyOf(profile));
        // Bug S2-01: gym đã sửa địa chỉ theo yêu cầu của Admin -> gỡ cờ cảnh báo và
        // ghi chú cũ. Admin soát lại ở vòng sau nếu địa chỉ mới vẫn chưa đạt.
        //
        // V59: phải gỡ TRƯỚC khi geocode. Làm sau thì cờ mà applyGeolocation vừa
        // bật vì địa chỉ mới cũng mơ hồ sẽ bị xoá ngay trong cùng một lần lưu, và
        // gym không bao giờ biết địa chỉ vừa nhập vẫn chưa đạt.
        if (addressChanged && !profile.isAddressVerified()) {
            profile.setAddressVerified(true);
            profile.setAddressReviewNote(null);
        }
        if (addressChanged || request.getLatitude() != null || request.getLongitude() != null
                || profile.getLatitude() == null) {
            applyGeolocation(profile, pinOf(request),
                    profile.getAddress(), profile.getDistrict(), profile.getCity());
        }
        gymProfileRepository.save(profile);
        log.info("Gym profile updated by {}", username);
        return toResponse(profile);
    }

    /**
     * UC-18 (V55): gắn toạ độ cho hồ sơ gym. Không phân giải được thì GIỮ NGUYÊN
     * giá trị cũ — Google lỗi/hết quota không được phép xoá trắng toạ độ đang có.
     *
     * <p>place_id/formatted_address chỉ được ghi khi phân giải thực sự trả về giá
     * trị mới. Ghi vô điều kiện là mỗi lần operator ghim toạ độ tay (resolution
     * không kèm metadata) lại xoá trắng hai cột đó, dù địa chỉ chẳng đổi.
     */
    private void applyGeolocation(GymProfile profile, com.fitmatch.service.support.AddressGeocoder.Pin pin,
                                  String address, String district, String city) {
        var resolution = addressGeocoder.resolve(pin, address, district, city);
        if (!resolution.resolved()) {
            return;
        }
        profile.setLatitude(resolution.latitude());
        profile.setLongitude(resolution.longitude());
        if (resolution.placeId() != null) {
            profile.setPlaceId(resolution.placeId());
            // V65: nhãn provider đi LIỀN với id, không bao giờ tách rời — id mới của
            // dịch vụ này mà còn nhãn cũ của dịch vụ kia là đúng kịch bản khiến job
            // làm mới tra nhầm địa điểm.
            profile.setPlaceProvider(resolution.placeProvider());
        }
        if (resolution.formattedAddress() != null) {
            profile.setFormattedAddress(resolution.formattedAddress());
        }
        profile.setLocationType(resolution.locationType());
        profile.setCoordinatesPinned(resolution.pinnedByUser());
        profile.setGeocodedAt(resolution.geocodedAt());
        // V59 (bug S2-01): Google chỉ khớp tới tâm phường/quận -> tự yêu cầu gym
        // xác minh lại, thay vì chờ Admin soát tay từng hồ sơ.
        com.fitmatch.service.support.AddressQuality.flagIfImprecise(profile, resolution.locationType());
    }

    /** Gom toạ độ + metadata Places mà client gửi kèm thành một {@code Pin}. */
    private static com.fitmatch.service.support.AddressGeocoder.Pin pinOf(SubmitGymRegistrationRequest r) {
        return new com.fitmatch.service.support.AddressGeocoder.Pin(
                r.getLatitude(), r.getLongitude(), r.getPlaceId(), r.getFormattedAddress(), r.getPlaceProvider(),
                Boolean.TRUE.equals(r.getCoordinatesPinned()));
    }

    private static com.fitmatch.service.support.AddressGeocoder.Pin pinOf(UpdateGymProfileRequest r) {
        return new com.fitmatch.service.support.AddressGeocoder.Pin(
                r.getLatitude(), r.getLongitude(), r.getPlaceId(), r.getFormattedAddress(), r.getPlaceProvider(),
                Boolean.TRUE.equals(r.getCoordinatesPinned()));
    }

    /** Khoá so sánh "địa chỉ có đổi không" cho {@link #updateProfile}. */
    private static String addressKeyOf(GymProfile profile) {
        return String.join("|",
                java.util.Objects.toString(profile.getAddress(), ""),
                java.util.Objects.toString(profile.getDistrict(), ""),
                java.util.Objects.toString(profile.getCity(), ""));
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

    @Override
    @Transactional(readOnly = true)
    public List<GymDocumentDto> listDocuments(String username) {
        GymProfile profile = requireOwnProfile(username);
        return gymDocumentRepository.findByGymProfile_Id(profile.getId()).stream()
                .map(GymDocumentDto::of).toList();
    }

    @Override
    @Transactional
    public GymDocumentDto addDocument(String username, GymDocumentDto request) {
        GymProfile profile = requireOwnProfile(username);
        assertDocumentsEditable(profile);
        GymDocument saved = gymDocumentRepository.save(GymDocument.builder()
                .gymProfile(profile)
                .documentType(request.getDocumentType())
                .fileUrl(request.getFileUrl())
                .build());
        log.info("Gym {} added verification document {}", username, saved.getId());
        return GymDocumentDto.of(saved);
    }

    @Override
    @Transactional
    public void deleteDocument(String username, Long documentId) {
        GymDocument doc = gymDocumentRepository
                .findByIdAndGymProfile_User_Username(documentId, username)
                .orElseThrow(() -> new ResourceNotFoundException("Gym document", documentId));
        assertDocumentsEditable(doc.getGymProfile());
        gymDocumentRepository.delete(doc);
        log.info("Gym {} deleted verification document {}", username, documentId);
    }

    /** UC-012: chỉ sửa tài liệu khi hồ sơ chưa/đang duyệt hoặc cần bổ sung (không phải APPROVED/SUSPENDED). */
    private void assertDocumentsEditable(GymProfile profile) {
        if (profile.getVerificationStatus() == VerificationStatus.APPROVED
                || profile.getVerificationStatus() == VerificationStatus.SUSPENDED) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Verification documents can only be changed while the application is draft, "
                            + "pending, or requires additional info (current: "
                            + profile.getVerificationStatus() + ")");
        }
    }

    private List<GymDocument> saveDocuments(GymProfile profile, SubmitGymRegistrationRequest request) {
        // P0-0.3: hợp nhất — chỉ thêm tài liệu có URL chưa tồn tại, giữ nguyên tài liệu
        // hiện có (idempotent). Với đăng ký mới (chưa có tài liệu) hành vi không đổi.
        List<GymDocument> existing = gymDocumentRepository.findByGymProfile_Id(profile.getId());
        java.util.Set<String> existingUrls = existing.stream()
                .map(GymDocument::getFileUrl)
                .collect(java.util.stream.Collectors.toSet());
        List<GymDocument> toSave = request.getDocuments().stream()
                .filter(d -> !existingUrls.contains(d.getFileUrl()))
                .map(d -> GymDocument.builder()
                        .gymProfile(profile)
                        .documentType(d.getDocumentType())
                        .fileUrl(d.getFileUrl())
                        .build())
                .toList();
        List<GymDocument> result = new java.util.ArrayList<>(existing);
        if (!toSave.isEmpty()) {
            result.addAll(gymDocumentRepository.saveAll(toSave));
        }
        return result;
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
