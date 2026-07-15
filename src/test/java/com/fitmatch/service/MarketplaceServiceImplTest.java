package com.fitmatch.service;

import com.fitmatch.common.enums.PtStatus;
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
    @Mock private com.fitmatch.repository.GymBranchRepository gymBranchRepository;
    @Mock private com.fitmatch.repository.GymServiceRepository gymServiceRepository;
    @Mock private com.fitmatch.repository.TrainingPackageRepository trainingPackageRepository;
    @Mock private com.fitmatch.repository.GymMediaRepository gymMediaRepository;
    @Mock private com.fitmatch.repository.OperatingHourRepository operatingHourRepository;
    @Mock private com.fitmatch.service.support.RatingAggregator ratingAggregator;
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
        when(ptProfileRepository.findByIdAndStatusAndGymProfile_VerificationStatusAndGymProfile_ActiveTrue(
                1L, PtStatus.ACTIVE, VerificationStatus.APPROVED))
                .thenReturn(Optional.of(p));
        when(ptCertificationRepository.findByPtProfile_Id(1L)).thenReturn(List.of());
        when(ratingAggregator.forPt(1L)).thenReturn(
                new com.fitmatch.service.support.RatingAggregator.Rating(
                        new java.math.BigDecimal("4.5"), 2));

        PtPublicProfileResponse res = service.getPtDetail(1L);

        assertThat(res.getDisplayName()).isEqualTo("Coach");
    }

    @Test
    void getPtDetail_notVisible_throws() {
        when(ptProfileRepository.findByIdAndStatusAndGymProfile_VerificationStatusAndGymProfile_ActiveTrue(
                2L, PtStatus.ACTIVE, VerificationStatus.APPROVED))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getPtDetail(2L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listGymServices_onlyPublishedOfVisibleGym() {
        when(gymProfileRepository.findByIdAndVerificationStatusAndActiveTrue(7L, VerificationStatus.APPROVED))
                .thenReturn(Optional.of(com.fitmatch.entity.GymProfile.builder().id(7L).build()));
        when(gymServiceRepository.findByGymProfile_IdAndStatus(7L, com.fitmatch.common.enums.CatalogStatus.PUBLISHED))
                .thenReturn(List.of(com.fitmatch.entity.GymService.builder()
                        .id(1L).name("Yoga").price(new java.math.BigDecimal("100.00")).build()));

        var result = service.listGymServices(7L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Yoga");
    }

    @Test
    void listGymCatalog_hiddenGym_throws404() {
        when(gymProfileRepository.findByIdAndVerificationStatusAndActiveTrue(9L, VerificationStatus.APPROVED))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listGymServices(9L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.listGymBranches(9L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.listGymPackages(9L)).isInstanceOf(ResourceNotFoundException.class);
    }
}
