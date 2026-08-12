package com.fitmatch.service;

import com.fitmatch.common.enums.GeocodingProvider;
import com.fitmatch.config.GeocodingProperties;
import com.fitmatch.service.impl.GeoapifyGeocodingService;
import com.fitmatch.service.support.GeoPoint;
import com.fitmatch.service.support.GeoSuggestion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;

/**
 * V65 (UC-18): bóc tách response của Geoapify.
 *
 * <p>Đáng test vì đây là dịch vụ MẶC ĐỊNH mới, và toàn bộ giá trị nằm ở những
 * chi tiết không tự lộ ra: tên field ({@code lon} chứ không phải {@code lng}),
 * cách Geoapify xếp đơn vị hành chính Việt Nam, và việc gắn đúng nhãn provider.
 * Sai một trong ba thứ đó thì hệ thống vẫn chạy, chỉ là dữ liệu sai lặng lẽ.
 */
class GeoapifyGeocodingServiceTest {

    private MockRestServiceServer server;
    private GeoapifyGeocodingService service;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();

        RestTemplateBuilder builder = mock(RestTemplateBuilder.class);
        when(builder.setConnectTimeout(any(Duration.class))).thenReturn(builder);
        when(builder.setReadTimeout(any(Duration.class))).thenReturn(builder);
        when(builder.build()).thenReturn(restTemplate);

        GeocodingProperties properties = new GeocodingProperties();
        properties.getGeoapify().setApiKey("test-key");
        service = new GeoapifyGeocodingService(properties, builder);
    }

    @Test
    void geocode_readsLonNotLng_andTagsProvider() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/geocode/search")))
                .andRespond(withSuccess("""
                        {"results":[{
                          "lat": 21.0002, "lon": 105.8010,
                          "formatted": "123 Nguyễn Trãi, Thanh Xuân, Hà Nội, Việt Nam",
                          "place_id": "51f2abc",
                          "result_type": "building",
                          "rank": {"confidence": 0.95}
                        }]}
                        """, MediaType.APPLICATION_JSON));

        Optional<GeoPoint> point = service.geocode("123 Nguyễn Trãi");

        assertThat(point).isPresent();
        assertThat(point.get().latitude()).isEqualByComparingTo("21.0002");
        assertThat(point.get().longitude()).isEqualByComparingTo("105.8010");
        assertThat(point.get().placeId()).isEqualTo("51f2abc");
        // Nhãn provider PHẢI đi cùng place_id, nếu không job làm mới sẽ bỏ qua bản ghi.
        assertThat(point.get().provider()).isEqualTo(GeocodingProvider.GEOAPIFY);
        // result_type=building + confidence cao -> đủ chính xác, không bắt xác minh lại.
        assertThat(point.get().locationType()).isEqualTo("ROOFTOP");
        server.verify();
    }

    /** Khớp tới cấp quận -> phải hạ về APPROXIMATE để cơ chế xác minh lại kích hoạt. */
    @Test
    void districtLevelMatch_isMarkedApproximate() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/geocode/search")))
                .andRespond(withSuccess("""
                        {"results":[{
                          "lat": 21.0, "lon": 105.8,
                          "formatted": "Thanh Xuân, Hà Nội",
                          "result_type": "district",
                          "rank": {"confidence": 0.9}
                        }]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(service.geocode("Thanh Xuân").orElseThrow().locationType())
                .isEqualTo("APPROXIMATE");
    }

    /** Không khớp địa điểm nào là kết quả nghiệp vụ bình thường, không phải sự cố. */
    @Test
    void emptyResults_yieldEmptyOptional() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/geocode/search")))
                .andRespond(withSuccess("{\"results\":[]}", MediaType.APPLICATION_JSON));

        assertThat(service.geocode("địa chỉ không tồn tại")).isEmpty();
    }

    /** Fail-soft: dịch vụ lỗi/hết hạn mức không được làm hỏng thao tác lưu hồ sơ gym. */
    @Test
    void serverError_isSwallowed() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/geocode/search")))
                .andRespond(withServerError());

        assertThat(service.geocode("bất kỳ")).isEmpty();
    }

    /**
     * Ánh xạ đơn vị hành chính Việt Nam — chỗ dễ sai nhất.
     *
     * <p>Hà Nội / TP.HCM là thành phố trực thuộc trung ương, OSM xếp ở admin_level 4
     * nên Geoapify trả về trong {@code state}, KHÔNG phải {@code city}. Quận nằm ở
     * {@code county}. Lấy nhầm field là form điền sai tỉnh/thành và gym rớt khỏi
     * bộ lọc marketplace.
     */
    @Test
    void autocomplete_mapsVietnameseAdminAreas() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/geocode/autocomplete")))
                .andExpect(queryParam("limit", "5"))
                .andRespond(withSuccess("""
                        {"results":[{
                          "lat": 21.0002, "lon": 105.8010,
                          "formatted": "123 Nguyễn Trãi, Thanh Xuân, Hà Nội, Việt Nam",
                          "address_line1": "123 Nguyễn Trãi",
                          "place_id": "51f2abc",
                          "county": "Thanh Xuân",
                          "state": "Hà Nội",
                          "city": "Hà Nội"
                        }]}
                        """, MediaType.APPLICATION_JSON));

        List<GeoSuggestion> suggestions = service.autocomplete("nguyen trai", 5);

        assertThat(suggestions).hasSize(1);
        GeoSuggestion first = suggestions.get(0);
        assertThat(first.label()).isEqualTo("123 Nguyễn Trãi");
        assertThat(first.formattedAddress()).isEqualTo("123 Nguyễn Trãi, Thanh Xuân, Hà Nội, Việt Nam");
        assertThat(first.district()).isEqualTo("Thanh Xuân");
        assertThat(first.city()).isEqualTo("Hà Nội");
        assertThat(first.provider()).isEqualTo(GeocodingProvider.GEOAPIFY);
        assertThat(first.latitude()).isEqualByComparingTo(new BigDecimal("21.0002"));
        server.verify();
    }

    /** Chưa cấu hình khoá -> im lặng trả rỗng, tuyệt đối không gọi mạng. */
    @Test
    void withoutApiKey_neverCallsTheNetwork() {
        GeocodingProperties empty = new GeocodingProperties();
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer strict = MockRestServiceServer.bindTo(restTemplate).build();
        RestTemplateBuilder builder = mock(RestTemplateBuilder.class);
        when(builder.setConnectTimeout(any(Duration.class))).thenReturn(builder);
        when(builder.setReadTimeout(any(Duration.class))).thenReturn(builder);
        when(builder.build()).thenReturn(restTemplate);

        GeoapifyGeocodingService disabled = new GeoapifyGeocodingService(empty, builder);

        assertThat(disabled.isEnabled()).isFalse();
        assertThat(disabled.geocode("bất kỳ")).isEmpty();
        assertThat(disabled.autocomplete("bất kỳ", 5)).isEmpty();
        // Không có request nào được kỳ vọng; verify sẽ nổ nếu service lỡ gọi đi.
        strict.verify();
    }
}
