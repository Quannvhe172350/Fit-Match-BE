package com.fitmatch.service;

import com.fitmatch.common.enums.GeocodingProvider;
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

    private static AddressGeocoder.Pin pin(String lat, String lng) {
        return new AddressGeocoder.Pin(new BigDecimal(lat), new BigDecimal(lng), null, null, null, false);
    }

    @Test
    void explicitCoordinatesWin_withoutCallingGoogle() {
        var result = addressGeocoder.resolve(pin("21.0287", "105.8524"),
                "1 Đinh Tiên Hoàng", "Hoàn Kiếm", "Hà Nội");

        assertThat(result.resolved()).isTrue();
        assertThat(result.latitude()).isEqualByComparingTo("21.0287");
        assertThat(result.geocodedAt()).isNotNull();
        verify(geocodingService, never()).geocode(anyString());
    }

    @Test
    void explicitPlaceMetadata_isCarriedThrough_soCallerNeedNotGeocodeJustForIt() {
        var result = addressGeocoder.resolve(
                new AddressGeocoder.Pin(new BigDecimal("21.0287"), new BigDecimal("105.8524"),
                        "place-42", "1 Đinh Tiên Hoàng, Hoàn Kiếm, Hà Nội", GeocodingProvider.GEOAPIFY, false),
                "1 Đinh Tiên Hoàng", "Hoàn Kiếm", "Hà Nội");

        assertThat(result.placeId()).isEqualTo("place-42");
        assertThat(result.formattedAddress()).isEqualTo("1 Đinh Tiên Hoàng, Hoàn Kiếm, Hà Nội");
        verify(geocodingService, never()).geocode(anyString());
    }

    @Test
    void explicitCoordinatesWithoutMetadata_leaveMetadataNull_meaning_noNewInfo_notWipe() {
        var result = addressGeocoder.resolve(
                new AddressGeocoder.Pin(new BigDecimal("21.0287"), new BigDecimal("105.8524"), "  ", "", null, false),
                "1 Đinh Tiên Hoàng", "Hoàn Kiếm", "Hà Nội");

        assertThat(result.resolved()).isTrue();
        assertThat(result.placeId()).isNull();
        assertThat(result.formattedAddress()).isNull();
    }

    @Test
    void invalidExplicitCoordinates_fallBackToGeocodingAddress() {
        when(geocodingService.geocode("1 Đinh Tiên Hoàng, Hoàn Kiếm, Hà Nội, Việt Nam"))
                .thenReturn(Optional.of(new GeoPoint(new BigDecimal("21.03"), new BigDecimal("105.85"),
                        "1 Đinh Tiên Hoàng, Hà Nội", "place-1", "ROOFTOP", GeocodingProvider.GOOGLE)));

        var result = addressGeocoder.resolve(pin("999", "105.8524"),
                "1 Đinh Tiên Hoàng", "Hoàn Kiếm", "Hà Nội");

        assertThat(result.resolved()).isTrue();
        assertThat(result.placeId()).isEqualTo("place-1");
        assertThat(result.formattedAddress()).isEqualTo("1 Đinh Tiên Hoàng, Hà Nội");
    }

    @Test
    void googleFindsNothing_resolvesToNone_soCallerKeepsExistingCoordinates() {
        when(geocodingService.geocode(anyString())).thenReturn(Optional.empty());

        var result = addressGeocoder.resolve(AddressGeocoder.Pin.none(), "địa chỉ rác", null, null);

        assertThat(result.resolved()).isFalse();
        assertThat(result.latitude()).isNull();
    }

    @Test
    void blankAddressAndNoCoordinates_neverCallsGoogle() {
        var result = addressGeocoder.resolve(AddressGeocoder.Pin.none(), null, null, "  ");

        assertThat(result.resolved()).isFalse();
        verify(geocodingService, never()).geocode(anyString());
    }

    /**
     * V60: cờ "người kéo ghim" phải đi hết đường từ request tới entity. Rơi mất ở
     * giữa thì job làm mới coi đó là toạ độ Google đoán và kéo ghim đi chỗ khác
     * sau 180 ngày — đúng thứ chủ gym vừa bỏ công sửa.
     */
    @Test
    void userPinnedFlag_survivesResolution() {
        var pinned = addressGeocoder.resolve(
                new AddressGeocoder.Pin(new BigDecimal("21.0287"), new BigDecimal("105.8524"),
                        "place-42", "1 Đinh Tiên Hoàng", GeocodingProvider.GEOAPIFY, true),
                "1 Đinh Tiên Hoàng", "Hoàn Kiếm", "Hà Nội");

        assertThat(pinned.pinnedByUser()).isTrue();
    }

    /**
     * V65 — vá lỗ hổng còn lại của đợt đổi provider.
     *
     * <p>Ô gợi ý địa chỉ giờ chạy qua proxy của chính server này, nên nhãn nguồn
     * của {@code place_id} là dữ liệu server tự cấp rồi nhận lại — không còn phải
     * đoán như khi ô đó nói chuyện thẳng với Google. Không mang nhãn qua thì mọi
     * hồ sơ lưu từ gợi ý đều bị đánh "không rõ nguồn" và không bao giờ được làm mới.
     */
    @Test
    void placeProviderFromPin_isCarriedThrough() {
        var result = addressGeocoder.resolve(
                new AddressGeocoder.Pin(new BigDecimal("21.0287"), new BigDecimal("105.8524"),
                        "geoapify-51f2", "1 Đinh Tiên Hoàng", GeocodingProvider.GEOAPIFY, false),
                "1 Đinh Tiên Hoàng", "Hoàn Kiếm", "Hà Nội");

        assertThat(result.placeId()).isEqualTo("geoapify-51f2");
        assertThat(result.placeProvider()).isEqualTo(GeocodingProvider.GEOAPIFY);
    }

    /** Gõ tay / kéo ghim mà không chọn gợi ý nào -> không rõ nguồn, và phải nói thẳng là null. */
    @Test
    void pinWithoutSuggestion_hasNoProviderLabel() {
        var result = addressGeocoder.resolve(pin("21.0287", "105.8524"),
                "1 Đinh Tiên Hoàng", "Hoàn Kiếm", "Hà Nội");

        assertThat(result.resolved()).isTrue();
        assertThat(result.placeProvider()).isNull();
    }

    /** Kết quả geocode phải mang nhãn của chính dịch vụ vừa trả lời. */
    @Test
    void geocodedResult_carriesResolvingProvider() {
        when(geocodingService.geocode(anyString()))
                .thenReturn(Optional.of(new GeoPoint(new BigDecimal("21.03"), new BigDecimal("105.85"),
                        "Địa chỉ", "place-1", "ROOFTOP", GeocodingProvider.GEOAPIFY)));

        var result = addressGeocoder.resolve(AddressGeocoder.Pin.none(), "12 Nguyễn Trãi", null, "Hà Nội");

        assertThat(result.placeProvider()).isEqualTo(GeocodingProvider.GEOAPIFY);
    }

    /** Ngược lại: kết quả geocode là máy đoán, không bao giờ được coi là ghim tay. */
    @Test
    void geocodedResult_isNeverMarkedAsUserPinned() {
        when(geocodingService.geocode(anyString()))
                .thenReturn(Optional.of(new GeoPoint(new BigDecimal("21.03"), new BigDecimal("105.85"),
                        "Địa chỉ", "place-1", "ROOFTOP", GeocodingProvider.GOOGLE)));

        var result = addressGeocoder.resolve(AddressGeocoder.Pin.none(), "12 Nguyễn Trãi", null, "Hà Nội");

        assertThat(result.pinnedByUser()).isFalse();
    }

    /** V59: chỉ APPROXIMATE mới đáng bắt xác minh lại; ba mức còn lại đủ để tới đúng nơi. */
    @Test
    void isImprecise_onlyFlagsApproximateResults() {
        assertThat(AddressGeocoder.isImprecise("APPROXIMATE")).isTrue();
        assertThat(AddressGeocoder.isImprecise("ROOFTOP")).isFalse();
        assertThat(AddressGeocoder.isImprecise("RANGE_INTERPOLATED")).isFalse();
        assertThat(AddressGeocoder.isImprecise("GEOMETRIC_CENTER")).isFalse();
        // Operator tự ghim trên bản đồ -> không có location_type, không phải "kém".
        assertThat(AddressGeocoder.isImprecise(null)).isFalse();
    }

    @Test
    void buildQuery_skipsBlankPartsAndAnchorsToVietnam() {
        assertThat(AddressGeocoder.buildQuery("12 Nguyễn Trãi", "  ", "Hà Nội"))
                .isEqualTo("12 Nguyễn Trãi, Hà Nội, Việt Nam");
        assertThat(AddressGeocoder.buildQuery(null, "", "  ")).isEmpty();
    }
}
