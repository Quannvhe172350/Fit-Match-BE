package com.fitmatch.service;

import com.fitmatch.common.enums.GeocodingProvider;
import com.fitmatch.config.GeocodingProperties;
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
    @Mock private GeocodingProperties geocodingProperties;
    @InjectMocks private GeocodingBackfillServiceImpl service;

    @BeforeEach
    void geocodingEnabled() {
        lenient().when(geocodingService.isEnabled()).thenReturn(true);
        lenient().when(geocodingProperties.getRefreshAfterDays()).thenReturn(180);
        // V65: job chỉ tra lại bản ghi do CHÍNH provider đang chạy cấp place_id.
        lenient().when(geocodingProperties.getProvider()).thenReturn(GeocodingProvider.GOOGLE);
        lenient().when(gymBranchRepository.findByPlaceIdIsNotNullAndCoordinatesPinnedFalseAndGeocodedAtBefore(any()))
                .thenReturn(List.of());
    }

    private static GymProfile staleGym() {
        return GymProfile.builder()
                .id(1L).placeId("place-1").placeProvider(GeocodingProvider.GOOGLE)
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
                        "Địa chỉ mới", "place-1", "ROOFTOP", GeocodingProvider.GOOGLE)));

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
                        "Địa chỉ cũ", "place-1", "ROOFTOP", GeocodingProvider.GOOGLE)));

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
        when(geocodingProperties.getRefreshAfterDays()).thenReturn(0);

        var result = service.refreshStale(50);

        assertThat(result.scanned()).isZero();
        verify(geocodingService, never()).geocodeByPlaceId(any());
    }

    /**
     * V65 — bảo vệ chống lỗi im lặng nguy hiểm nhất của đợt đổi provider.
     *
     * <p>place_id của Google đem hỏi Geoapify KHÔNG trả về lỗi, nó trả về một địa
     * điểm khác. Không có bộ lọc này thì lần chạy đầu sau khi đổi provider sẽ dời
     * ghim của toàn bộ hồ sơ cũ sang chỗ khác, lúc 3 giờ sáng, không một dòng log.
     */
    @Test
    void placeIdFromAnotherProvider_isNeverLookedUp() {
        when(geocodingProperties.getProvider()).thenReturn(GeocodingProvider.GEOAPIFY);
        GymProfile gym = staleGym(); // placeProvider = GOOGLE
        when(gymProfileRepository.findByPlaceIdIsNotNullAndCoordinatesPinnedFalseAndGeocodedAtBefore(any()))
                .thenReturn(List.of(gym));

        var result = service.refreshStale(50);

        assertThat(result.scanned()).isZero();
        verify(geocodingService, never()).geocodeByPlaceId(any());
        assertThat(gym.getLatitude()).isEqualByComparingTo("21.0290000");
    }

    /** Nguồn không rõ (place_id do ô gợi ý phía FE cấp) cũng phải bỏ qua, không đoán bừa. */
    @Test
    void placeIdWithUnknownProvider_isNeverLookedUp() {
        GymProfile gym = staleGym();
        gym.setPlaceProvider(null);
        when(gymProfileRepository.findByPlaceIdIsNotNullAndCoordinatesPinnedFalseAndGeocodedAtBefore(any()))
                .thenReturn(List.of(gym));

        var result = service.refreshStale(50);

        assertThat(result.scanned()).isZero();
        verify(geocodingService, never()).geocodeByPlaceId(any());
    }
}
