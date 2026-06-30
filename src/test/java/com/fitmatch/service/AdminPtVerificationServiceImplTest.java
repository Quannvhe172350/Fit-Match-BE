package com.fitmatch.service;

import com.fitmatch.common.enums.Role;
import com.fitmatch.common.enums.VerificationStatus;
import com.fitmatch.entity.PtProfile;
import com.fitmatch.entity.User;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.repository.PtDocumentRepository;
import com.fitmatch.repository.PtProfileRepository;
import com.fitmatch.repository.UserRepository;
import com.fitmatch.service.impl.AdminPtVerificationServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminPtVerificationServiceImplTest {

    @Mock private PtProfileRepository ptProfileRepository;
    @Mock private PtDocumentRepository ptDocumentRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditService auditService;
    @InjectMocks private AdminPtVerificationServiceImpl service;

    @Test
    void approve_pending_promotesUserToPt() {
        User user = User.builder().id(1L).username("john").role(Role.ROLE_CUSTOMER).build();
        PtProfile profile = PtProfile.builder().id(5L).user(user)
                .verificationStatus(VerificationStatus.PENDING).active(false).build();
        when(ptProfileRepository.findById(5L)).thenReturn(Optional.of(profile));
        when(ptDocumentRepository.findByPtProfile_Id(5L)).thenReturn(List.of());

        service.approve(5L, "admin");

        assertThat(profile.getVerificationStatus()).isEqualTo(VerificationStatus.APPROVED);
        assertThat(profile.isActive()).isTrue();
        assertThat(user.getRole()).isEqualTo(Role.ROLE_PT);
    }

    @Test
    void approve_notPending_throws() {
        PtProfile profile = PtProfile.builder().id(5L).user(User.builder().build())
                .verificationStatus(VerificationStatus.APPROVED).build();
        when(ptProfileRepository.findById(5L)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.approve(5L, "admin")).isInstanceOf(BusinessException.class);
    }

    @Test
    void reject_pending_setsReason() {
        PtProfile profile = PtProfile.builder().id(5L).user(User.builder().username("john").build())
                .verificationStatus(VerificationStatus.PENDING).build();
        when(ptProfileRepository.findById(5L)).thenReturn(Optional.of(profile));
        when(ptDocumentRepository.findByPtProfile_Id(5L)).thenReturn(List.of());

        service.reject(5L, "Missing cert", "admin");

        assertThat(profile.getVerificationStatus()).isEqualTo(VerificationStatus.REJECTED);
        assertThat(profile.getRejectionReason()).isEqualTo("Missing cert");
    }
}
