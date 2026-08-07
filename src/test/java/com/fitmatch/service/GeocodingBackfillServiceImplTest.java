package com.fitmatch.service;

import com.fitmatch.config.GoogleMapsProperties;
import com.fitmatch.entity.GymProfile;
import com.fitmatch.repository.GymBranchRepository;
import com.fitmatch.repository.GymProfileRepository;
import com.fitmatch.service.impl.GeocodingBackfillServiceImpl;
import com.fitmatch.service.support.GeoPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** V59 (UC-18): pha làm mới toạ độ cũ của job backfill. */
@ExtendWith(MockitoExtension.class)
class GeocodingBackfillServiceImplTest {

    @Mock private GymProfileRepository gymProfileRepository;
    @Mock private GymBranchRepository gymBranchRepository;
    @Mock private GeocodingService geocodingService;
    @Mock private GoogleMapsProperties googleMapsProperties;
    @InjectMocks private GeocodingBackfillServiceImpl service;

    @BeforeEach
    void geocodingEnabled() {
        lenient().when(geocodingService.isEnabled()).thenReturn(true);
        lenient().when(googleMapsProperties.getRefreshAfterDays()).thenReturn(180);
        lenient().when(gymBranchRepository.findByPlaceIdIsNotNullAndCoordinatesPinnedFalseAndGeocodedAtBefore(any()))
                .thenReturn(List.of());
    }

    private static GymProfile staleGym() {
        return GymProfile.builder()
                .id(1L).placeId("place-1")
                .latitude(new BigDecimal("21.0290000")).longitude(new BigDecimal("105.8526000"))
                .geocodedAt(LocalDateTime.now().minusYears(1))
                .addressVerified(true)
                .build();
    }

    @Test
    void movedPlace_updatesCoordinatesAndCounts() {
        GymProfile gym = staleGym();
        when(gymProfileRepository.findByPlaceIdIsNotNullAndCoordinatesPinnedFalseAndGeocodedAtBefore(any())).thenReturn(List.of(gym));
        when(geocodingService.geocodeByPlaceId("place-1")).thenReturn(Optional.of(
                new GeoPoint(new BigDecimal("21.0300000"), new BigDecimal("105.8530000"),
                        "Địa chỉ mới", "place-1", "ROOFTOP")));

        var result = service.refreshStale(50);

        assertThat(result.scanned()).isEqualTo(1);
        assertThat(result.updated()).isEqualTo(1);
        assertThat(gym.getLatitude()).isEqualByComparingTo("21.0300000");
        assertThat(gym.getFormattedAddress()).isEqualTo("Địa chỉ mới");
    }

    /** Toạ độ y hệt vẫn phải dập lại geocodedAt, nếu không bản ghi bị tra lại mỗi đêm mãi mãi. */
    @Test
    void unchangedPlace_refreshesTimestampButIsNotCountedAsUpdated() {
        GymProfile gym = staleGym();
        LocalDateTime before = gym.getGeocodedAt();
        when(gymProfileRepository.findByPlaceIdIsNotNullAndCoordinatesPinnedFalseAndGeocodedAtBefore(any())).thenReturn(List.of(gym));
        // Cùng giá trị số nhưng khác scale — BigDecimal.equals sẽ báo "đã đổi".
        when(geocodingService.geocodeByPlaceId("place-1")).thenReturn(Optional.of(
                new GeoPoint(new BigDecimal("21.029"), new BigDecimal("105.8526"),
                        "Địa chỉ cũ", "place-1", "ROOFTOP")));

        var result = service.refreshStale(50);

        assertThat(result.updated()).isZero();
        assertThat(gym.getGeocodedAt()).isAfter(before);
    }

    /** Địa điểm bị gỡ khỏi Google: giữ nguyên toạ độ đang có, đừng xoá trắng. */
    @Test
    void placeGone_keepsExistingCoordinates() {
        GymProfile gym = staleGym();
        when(gymProfileRepository.findByPlaceIdIsNotNullAndCoordinatesPinnedFalseAndGeocodedAtBefore(any())).thenReturn(List.of(gym));
        when(geocodingService.geocodeByPlaceId("place-1")).thenReturn(Optional.empty());

        service.refreshStale(50);

        assertThat(gym.getLatitude()).isEqualByComparingTo("21.0290000");
        assertThat(gym.getGeocodedAt()).isAfter(LocalDateTime.now().minusMinutes(1));
    }

    @Test
    void refreshDisabled_neverCallsGoogle() {
        when(googleMapsProperties.getRefreshAfterDays()).thenReturn(0);

        var result = service.refreshStale(50);

        assertThat(result.scanned()).isZero();
        verify(geocodingService, never()).geocodeByPlaceId(any());
    }
}
