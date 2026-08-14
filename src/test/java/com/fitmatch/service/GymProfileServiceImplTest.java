package com.fitmatch.service;

import com.fitmatch.common.enums.VerificationStatus;
import com.fitmatch.dto.gym.GymDocumentDto;
import com.fitmatch.dto.gym.GymProfileResponse;
import com.fitmatch.dto.gym.SubmitGymRegistrationRequest;
import com.fitmatch.entity.GymProfile;
import com.fitmatch.entity.User;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.repository.GymDocumentRepository;
import com.fitmatch.repository.GymProfileRepository;
import com.fitmatch.repository.UserRepository;
import com.fitmatch.service.impl.GymProfileServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GymProfileServiceImplTest {

    @Mock private GymProfileRepository gymProfileRepository;
    @Mock private GymDocumentRepository gymDocumentRepository;
    @Mock private UserRepository userRepository;
    @Mock private com.fitmatch.repository.GymBranchRepository gymBranchRepository;
    // UC-18 (V55): geocode là bước làm giàu dữ liệu fail-soft; mock mặc định trả
    // Resolution.none() nên các test hồ sơ bên dưới không bị đổi hành vi.
    @Mock private com.fitmatch.service.support.AddressGeocoder addressGeocoder;
    @InjectMocks private GymProfileServiceImpl service;

    @org.junit.jupiter.api.BeforeEach
    void geocoderReturnsNothing() {
        org.mockito.Mockito.lenient()
                .when(addressGeocoder.resolve(any(), any(), any(), any()))
                .thenReturn(com.fitmatch.service.support.AddressGeocoder.Resolution.none());
    }

    private SubmitGymRegistrationRequest request() {
        return SubmitGymRegistrationRequest.builder()
                .gymName("Iron Gym")
                .documents(List.of(GymDocumentDto.builder().documentType("LICENSE").fileUrl("http://x/l.png").build()))
                .build();
    }

    @Test
    void submitRegistration_createsPendingProfile() {
        User user = User.builder().id(1L).username("ops").build();
        when(gymProfileRepository.existsByUser_Username("ops")).thenReturn(false);
        when(userRepository.findByUsername("ops")).thenReturn(Optional.of(user));
        when(gymProfileRepository.save(any(GymProfile.class))).thenAnswer(inv -> {
            GymProfile p = inv.getArgument(0);
            p.setId(20L);
            return p;
        });
        when(gymDocumentRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        GymProfileResponse res = service.submitRegistration("ops", request());

        assertThat(res.getVerificationStatus()).isEqualTo(VerificationStatus.PENDING);
        assertThat(res.isActive()).isFalse();
        assertThat(res.getDocuments()).hasSize(1);
    }

    @Test
    void submitRegistration_existingProfile_throwsConflict() {
        when(gymProfileRepository.existsByUser_Username("ops")).thenReturn(true);

        assertThatThrownBy(() -> service.submitRegistration("ops", request()))
                .isInstanceOf(BusinessException.class);
        verify(gymProfileRepository, never()).save(any());
    }

    @Test
    void resubmit_whenRejected_setsPending() {
        GymProfile profile = GymProfile.builder().id(20L).user(User.builder().username("ops").build())
                .verificationStatus(VerificationStatus.REJECTED).rejectionReason("bad").build();
        when(gymProfileRepository.findByUser_Username("ops")).thenReturn(Optional.of(profile));
        when(gymDocumentRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        GymProfileResponse res = service.resubmitRegistration("ops", request());

        assertThat(res.getVerificationStatus()).isEqualTo(VerificationStatus.PENDING);
        assertThat(profile.getRejectionReason()).isNull();
        // P0-0.3: resubmit KHÔNG được xóa sạch tài liệu (tránh mất tài liệu quản lý qua
        // DocumentManager); chỉ hợp nhất thêm tài liệu mới.
        verify(gymDocumentRepository, never()).deleteByGymProfile_Id(20L);
        assertThat(res.getDocuments()).hasSize(1);
    }

    @Test
    void resubmit_preservesExistingDocumentsAndMergesNew() {
        // P0-0.3: tài liệu đã có (vd thêm qua DocumentManager) phải được giữ; chỉ thêm URL mới.
        GymProfile profile = GymProfile.builder().id(20L).user(User.builder().username("ops").build())
                .verificationStatus(VerificationStatus.REQUIRES_INFO).build();
        com.fitmatch.entity.GymDocument existingDoc = com.fitmatch.entity.GymDocument.builder()
                .gymProfile(profile).documentType("ID").fileUrl("http://x/existing.png").build();
        when(gymProfileRepository.findByUser_Username("ops")).thenReturn(Optional.of(profile));
        when(gymDocumentRepository.findByGymProfile_Id(20L)).thenReturn(List.of(existingDoc));
        when(gymDocumentRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        // request() mang doc mới url "http://x/l.png" -> khác doc cũ -> merge thành 2.
        GymProfileResponse res = service.resubmitRegistration("ops", request());

        assertThat(res.getDocuments()).hasSize(2);
        verify(gymDocumentRepository, never()).deleteByGymProfile_Id(any());
    }

    @Test
    void resubmit_whenPending_throws() {
        GymProfile profile = GymProfile.builder().id(20L).user(User.builder().username("ops").build())
                .verificationStatus(VerificationStatus.PENDING).build();
        when(gymProfileRepository.findByUser_Username("ops")).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.resubmitRegistration("ops", request()))
                .isInstanceOf(BusinessException.class);
    }

    // ---------- tên gym sau khi được xác minh ----------

    private GymProfile approvedGym(String name) {
        GymProfile profile = GymProfile.builder().id(20L)
                .user(User.builder().username("ops").build())
                .gymName(name)
                .verificationStatus(VerificationStatus.APPROVED)
                .build();
        when(gymProfileRepository.findByUser_Username("ops")).thenReturn(Optional.of(profile));
        return profile;
    }

    /**
     * Tên đã duyệt là danh tính pháp nhân Admin đã đối chiếu giấy tờ, và là cái
     * tên đang nằm trên vé khách đã mua.
     */
    @Test
    void updateProfile_approvedGym_cannotRenameItself() {
        approvedGym("Gym A");

        assertThatThrownBy(() -> service.updateProfile("ops",
                com.fitmatch.dto.gym.UpdateGymProfileRequest.builder().gymName("Gym B").build()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã được xác minh");
    }

    /**
     * Form của gym gửi lại nguyên vẹn mọi trường. Gửi đúng tên cũ KHÔNG phải là
     * đổi tên — chặn theo "có gửi tên" sẽ làm hỏng cả lần chỉ sửa số điện thoại.
     */
    @Test
    void updateProfile_approvedGym_sameNameIsNotARename() {
        GymProfile profile = approvedGym("Gym A");

        service.updateProfile("ops", com.fitmatch.dto.gym.UpdateGymProfileRequest.builder()
                .gymName("Gym A").phone("0912345678").build());

        assertThat(profile.getGymName()).isEqualTo("Gym A");
        assertThat(profile.getPhone()).isEqualTo("0912345678");
    }

    /** Chưa duyệt thì vẫn sửa tên bình thường — luật chỉ khoá sau khi xác minh. */
    @Test
    void updateProfile_pendingGym_canStillRename() {
        GymProfile profile = GymProfile.builder().id(20L)
                .user(User.builder().username("ops").build())
                .gymName("Gym A")
                .verificationStatus(VerificationStatus.PENDING)
                .build();
        when(gymProfileRepository.findByUser_Username("ops")).thenReturn(Optional.of(profile));

        service.updateProfile("ops",
                com.fitmatch.dto.gym.UpdateGymProfileRequest.builder().gymName("Gym B").build());

        assertThat(profile.getGymName()).isEqualTo("Gym B");
    }
}
