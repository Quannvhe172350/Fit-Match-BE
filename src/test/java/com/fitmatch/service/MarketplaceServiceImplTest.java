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
import static org.mockito.Mockito.verify;
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
    @Mock private com.fitmatch.config.GoogleMapsProperties googleMapsProperties;
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

    // ----- UC-18 (V55): tìm theo bán kính -----

    private static final java.math.BigDecimal HOAN_KIEM_LAT = new java.math.BigDecimal("21.0287");
    private static final java.math.BigDecimal HOAN_KIEM_LNG = new java.math.BigDecimal("105.8524");

    /** Không có toạ độ -> đi nhánh Specification cũ, KHÔNG chạm truy vấn native. */
    @Test
    void searchGyms_withoutCoordinates_usesSpecificationBranch() {
        when(gymProfileRepository.findAll(org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<com.fitmatch.entity.GymProfile>>any(),
                org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(org.springframework.data.domain.Page.empty());

        var result = service.searchGyms(
                new com.fitmatch.dto.gym.GymSearchCriteria("yoga", null, null, null, null, null, null, null),
                org.springframework.data.domain.PageRequest.of(0, 20));

        assertThat(result.getContent()).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(gymBranchRepository);
    }

    /**
     * Gym có trụ sở xa nhưng chi nhánh ngay cạnh người dùng: khoảng cách và marker
     * phải lấy theo CHI NHÁNH, nếu không card hiện "cách 1km" mà ghim ở tận trụ sở.
     */
    @Test
    void searchGyms_nearby_reportsNearestBranchNotHeadquarters() {
        var gym = com.fitmatch.entity.GymProfile.builder()
                .id(5L).gymName("California Fitness")
                .latitude(new java.math.BigDecimal("21.0369"))   // Lăng Bác, ~2km
                .longitude(new java.math.BigDecimal("105.8350"))
                .build();
        var branch = com.fitmatch.entity.GymBranch.builder()
                .id(11L).name("CN Hoàn Kiếm").gymProfile(gym)
                .latitude(new java.math.BigDecimal("21.0290"))   // sát Hồ Gươm, ~0.03km
                .longitude(new java.math.BigDecimal("105.8526"))
                .build();

        stubNearbyPage(view(5L, 0.03));
        when(gymProfileRepository.findAllById(List.of(5L))).thenReturn(List.of(gym));
        when(gymBranchRepository.findByGymProfile_IdInAndActiveTrue(List.of(5L))).thenReturn(List.of(branch));
        when(gymMediaRepository.findByGymProfile_Id(5L)).thenReturn(List.of());
        when(ratingAggregator.forGym(5L)).thenReturn(
                new com.fitmatch.service.support.RatingAggregator.Rating(new java.math.BigDecimal("4.5"), 3));

        var result = service.searchGyms(
                new com.fitmatch.dto.gym.GymSearchCriteria(null, null, null, null, null,
                        HOAN_KIEM_LAT, HOAN_KIEM_LNG, 5.0),
                org.springframework.data.domain.PageRequest.of(0, 20));

        var card = result.getContent().get(0);
        assertThat(card.getNearestBranchName()).isEqualTo("CN Hoàn Kiếm");
        assertThat(card.getLatitude()).isEqualByComparingTo("21.0290");
        assertThat(card.getDistanceKm()).isLessThan(0.5);
    }

    /** Bán kính client gửi vượt trần cấu hình phải bị kẹp trước khi vào truy vấn. */
    @Test
    void searchGyms_nearby_clampsRadiusToConfiguredMaximum() {
        when(googleMapsProperties.getMaxSearchRadiusKm()).thenReturn(50.0);
        stubNearbyPage();

        service.searchGyms(new com.fitmatch.dto.gym.GymSearchCriteria(null, null, null, null, null,
                        HOAN_KIEM_LAT, HOAN_KIEM_LNG, 9999.0),
                org.springframework.data.domain.PageRequest.of(0, 20));

        var radius = org.mockito.ArgumentCaptor.forClass(Double.class);
        verify(gymProfileRepository).searchNearby(org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble(), radius.capture(),
                org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class));
        assertThat(radius.getValue()).isEqualTo(50.0);
    }

    /** Bỏ trống radius -> dùng mặc định cấu hình, không phải 0 (sẽ không ra kết quả nào). */
    @Test
    void searchGyms_nearby_missingRadiusUsesConfiguredDefault() {
        when(googleMapsProperties.getMaxSearchRadiusKm()).thenReturn(50.0);
        when(googleMapsProperties.getDefaultSearchRadiusKm()).thenReturn(5.0);
        stubNearbyPage();

        service.searchGyms(new com.fitmatch.dto.gym.GymSearchCriteria(null, null, null, null, null,
                        HOAN_KIEM_LAT, HOAN_KIEM_LNG, null),
                org.springframework.data.domain.PageRequest.of(0, 20));

        var radius = org.mockito.ArgumentCaptor.forClass(Double.class);
        verify(gymProfileRepository).searchNearby(org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble(), radius.capture(),
                org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class));
        assertThat(radius.getValue()).isEqualTo(5.0);
    }

    /** Keyword phải tới truy vấn dưới dạng pattern LIKE viết thường (SQL không tự bọc %). */
    @Test
    void searchGyms_nearby_normalisesTextFiltersToLikePatterns() {
        when(googleMapsProperties.getMaxSearchRadiusKm()).thenReturn(50.0);
        stubNearbyPage();

        service.searchGyms(new com.fitmatch.dto.gym.GymSearchCriteria("  California ", "Hà Nội", null,
                        null, null, HOAN_KIEM_LAT, HOAN_KIEM_LNG, 3.0),
                org.springframework.data.domain.PageRequest.of(0, 20));

        var keyword = org.mockito.ArgumentCaptor.forClass(String.class);
        var city = org.mockito.ArgumentCaptor.forClass(String.class);
        var district = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(gymProfileRepository).searchNearby(org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble(),
                keyword.capture(), city.capture(), district.capture(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class));
        assertThat(keyword.getValue()).isEqualTo("%california%");
        assertThat(city.getValue()).isEqualTo("%hà nội%");
        assertThat(district.getValue()).isNull();
    }

    private void stubNearbyPage(com.fitmatch.repository.projection.GymDistanceView... views) {
        when(gymProfileRepository.searchNearby(org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(views)));
    }

    private static com.fitmatch.repository.projection.GymDistanceView view(long gymId, double distanceKm) {
        return new com.fitmatch.repository.projection.GymDistanceView() {
            @Override public Long getGymId() {
                return gymId;
            }

            @Override public Double getDistanceKm() {
                return distanceKm;
            }
        };
    }
}
