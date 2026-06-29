package com.fitmatch.service;

import com.fitmatch.common.enums.VerificationStatus;
import com.fitmatch.dto.pt.PtDocumentDto;
import com.fitmatch.dto.pt.PtProfileResponse;
import com.fitmatch.dto.pt.SubmitPtRegistrationRequest;
import com.fitmatch.entity.PtProfile;
import com.fitmatch.entity.User;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.repository.PtDocumentRepository;
import com.fitmatch.repository.PtProfileRepository;
import com.fitmatch.repository.UserRepository;
import com.fitmatch.service.impl.PtProfileServiceImpl;
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
class PtProfileServiceImplTest {

    @Mock private PtProfileRepository ptProfileRepository;
    @Mock private PtDocumentRepository ptDocumentRepository;
    @Mock private UserRepository userRepository;
    @InjectMocks private PtProfileServiceImpl service;

    private SubmitPtRegistrationRequest request() {
        return SubmitPtRegistrationRequest.builder()
                .displayName("Coach John")
                .documents(List.of(PtDocumentDto.builder().documentType("ID").fileUrl("http://x/id.png").build()))
                .build();
    }

    @Test
    void submitRegistration_createsPendingProfile() {
        User user = User.builder().id(1L).username("john").build();
        when(ptProfileRepository.existsByUser_Username("john")).thenReturn(false);
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(user));
        when(ptProfileRepository.save(any(PtProfile.class))).thenAnswer(inv -> {
            PtProfile p = inv.getArgument(0);
            p.setId(10L);
            return p;
        });

        PtProfileResponse res = service.submitRegistration("john", request());

        assertThat(res.getVerificationStatus()).isEqualTo(VerificationStatus.PENDING);
        assertThat(res.isActive()).isFalse();
        assertThat(res.getDocuments()).hasSize(1);
        verify(ptDocumentRepository).saveAll(any());
    }

    @Test
    void submitRegistration_existingProfile_throwsConflict() {
        when(ptProfileRepository.existsByUser_Username("john")).thenReturn(true);

        assertThatThrownBy(() -> service.submitRegistration("john", request()))
                .isInstanceOf(BusinessException.class);
        verify(ptProfileRepository, never()).save(any());
    }

    @Test
    void resubmit_whenRejected_setsPending() {
        PtProfile profile = PtProfile.builder().id(10L).user(User.builder().username("john").build())
                .verificationStatus(VerificationStatus.REJECTED).rejectionReason("bad docs").build();
        when(ptProfileRepository.findByUser_Username("john")).thenReturn(Optional.of(profile));

        PtProfileResponse res = service.resubmitRegistration("john", request());

        assertThat(res.getVerificationStatus()).isEqualTo(VerificationStatus.PENDING);
        assertThat(profile.getRejectionReason()).isNull();
        verify(ptDocumentRepository).deleteByPtProfile_Id(10L);
        verify(ptDocumentRepository).saveAll(any());
    }

    @Test
    void resubmit_whenPending_throws() {
        PtProfile profile = PtProfile.builder().id(10L).user(User.builder().username("john").build())
                .verificationStatus(VerificationStatus.PENDING).build();
        when(ptProfileRepository.findByUser_Username("john")).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.resubmitRegistration("john", request()))
                .isInstanceOf(BusinessException.class);
    }
}
