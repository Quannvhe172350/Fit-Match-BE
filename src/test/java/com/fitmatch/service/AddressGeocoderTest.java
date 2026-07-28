package com.fitmatch.service;

import com.fitmatch.service.support.AddressGeocoder;
import com.fitmatch.service.support.GeoPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AddressGeocoderTest {

    @Mock private GeocodingService geocodingService;
    @InjectMocks private AddressGeocoder addressGeocoder;

    @Test
    void explicitCoordinatesWin_withoutCallingGoogle() {
        var result = addressGeocoder.resolve(new BigDecimal("21.0287"), new BigDecimal("105.8524"),
                "1 Đinh Tiên Hoàng", "Hoàn Kiếm", "Hà Nội");

        assertThat(result.resolved()).isTrue();
        assertThat(result.latitude()).isEqualByComparingTo("21.0287");
        assertThat(result.geocodedAt()).isNotNull();
        verify(geocodingService, never()).geocode(anyString());
    }

    @Test
    void invalidExplicitCoordinates_fallBackToGeocodingAddress() {
        when(geocodingService.geocode("1 Đinh Tiên Hoàng, Hoàn Kiếm, Hà Nội, Việt Nam"))
                .thenReturn(Optional.of(new GeoPoint(new BigDecimal("21.03"), new BigDecimal("105.85"),
                        "1 Đinh Tiên Hoàng, Hà Nội", "place-1")));

        var result = addressGeocoder.resolve(new BigDecimal("999"), new BigDecimal("105.8524"),
                "1 Đinh Tiên Hoàng", "Hoàn Kiếm", "Hà Nội");

        assertThat(result.resolved()).isTrue();
        assertThat(result.placeId()).isEqualTo("place-1");
        assertThat(result.formattedAddress()).isEqualTo("1 Đinh Tiên Hoàng, Hà Nội");
    }

    @Test
    void googleFindsNothing_resolvesToNone_soCallerKeepsExistingCoordinates() {
        when(geocodingService.geocode(anyString())).thenReturn(Optional.empty());

        var result = addressGeocoder.resolve(null, null, "địa chỉ rác", null, null);

        assertThat(result.resolved()).isFalse();
        assertThat(result.latitude()).isNull();
    }

    @Test
    void blankAddressAndNoCoordinates_neverCallsGoogle() {
        var result = addressGeocoder.resolve(null, null, null, null, "  ");

        assertThat(result.resolved()).isFalse();
        verify(geocodingService, never()).geocode(anyString());
    }

    @Test
    void buildQuery_skipsBlankPartsAndAnchorsToVietnam() {
        assertThat(AddressGeocoder.buildQuery("12 Nguyễn Trãi", "  ", "Hà Nội"))
                .isEqualTo("12 Nguyễn Trãi, Hà Nội, Việt Nam");
        assertThat(AddressGeocoder.buildQuery(null, "", "  ")).isEmpty();
    }
}
