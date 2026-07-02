package com.fitmatch.service;

import com.fitmatch.common.enums.VerificationStatus;
import com.fitmatch.dto.pt.PtPublicProfileResponse;
import com.fitmatch.entity.PtProfile;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.PtCertificationRepository;
import com.fitmatch.repository.PtProfileRepository;
import com.fitmatch.service.impl.MarketplaceServiceImpl;
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
class MarketplaceServiceImplTest {

    @Mock private PtProfileRepository ptProfileRepository;
    @Mock private PtCertificationRepository ptCertificationRepository;
    @Mock private com.fitmatch.repository.GymProfileRepository gymProfileRepository;
    @InjectMocks private MarketplaceServiceImpl service;

    @Test
    void getGymDetail_notVisible_throws() {
        when(gymProfileRepository.findByIdAndVerificationStatusAndActiveTrue(3L, VerificationStatus.APPROVED))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getGymDetail(3L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getPtDetail_onlyApprovedActive() {
        PtProfile p = PtProfile.builder().id(1L).displayName("Coach").build();
        when(ptProfileRepository.findByIdAndVerificationStatusAndActiveTrue(1L, VerificationStatus.APPROVED))
                .thenReturn(Optional.of(p));
        when(ptCertificationRepository.findByPtProfile_Id(1L)).thenReturn(List.of());

        PtPublicProfileResponse res = service.getPtDetail(1L);

        assertThat(res.getDisplayName()).isEqualTo("Coach");
    }

    @Test
    void getPtDetail_notVisible_throws() {
        when(ptProfileRepository.findByIdAndVerificationStatusAndActiveTrue(2L, VerificationStatus.APPROVED))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getPtDetail(2L)).isInstanceOf(ResourceNotFoundException.class);
    }
}
