package com.fitmatch.service;

import com.fitmatch.common.enums.Role;
import com.fitmatch.common.enums.VerificationStatus;
import com.fitmatch.entity.GymProfile;
import com.fitmatch.entity.User;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.repository.GymDocumentRepository;
import com.fitmatch.repository.GymProfileRepository;
import com.fitmatch.repository.UserRepository;
import com.fitmatch.service.impl.AdminGymVerificationServiceImpl;
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
class AdminGymVerificationServiceImplTest {

    @Mock private GymProfileRepository gymProfileRepository;
    @Mock private GymDocumentRepository gymDocumentRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditService auditService;
    @InjectMocks private AdminGymVerificationServiceImpl service;

    @Test
    void approve_pending_promotesUserToGymOperator() {
        User user = User.builder().id(1L).username("ops").role(Role.ROLE_CUSTOMER).build();
        GymProfile profile = GymProfile.builder().id(8L).user(user)
                .verificationStatus(VerificationStatus.PENDING).active(false).build();
        when(gymProfileRepository.findById(8L)).thenReturn(Optional.of(profile));
        when(gymDocumentRepository.findByGymProfile_Id(8L)).thenReturn(List.of());

        service.approve(8L, "admin");

        assertThat(profile.getVerificationStatus()).isEqualTo(VerificationStatus.APPROVED);
        assertThat(profile.isActive()).isTrue();
        assertThat(user.getRole()).isEqualTo(Role.ROLE_GYM_OPERATOR);
    }

    @Test
    void reject_notPending_throws() {
        GymProfile profile = GymProfile.builder().id(8L).user(User.builder().build())
                .verificationStatus(VerificationStatus.REJECTED).build();
        when(gymProfileRepository.findById(8L)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.reject(8L, "reason", "admin")).isInstanceOf(BusinessException.class);
    }
}
