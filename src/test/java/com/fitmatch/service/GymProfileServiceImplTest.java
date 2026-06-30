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
    @InjectMocks private GymProfileServiceImpl service;

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
}
