package com.fitmatch.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fitmatch.config.GoogleMapsProperties;
import com.fitmatch.service.GeocodingService;
import com.fitmatch.service.support.GeoPoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

/**
 * Triển khai {@link GeocodingService} trên Google Geocoding API (UC-18).
 *
 * <p>Chỉ đọc field cần dùng của response (lat/lng/formatted_address/place_id)
 * qua {@link JsonNode} — Google thêm field mới thường xuyên, bind vào DTO cứng
 * sẽ gãy không cần thiết.
 */
@Slf4j
@Service
public class GoogleGeocodingService implements GeocodingService {

    private static final String GEOCODE_URL = "https://maps.googleapis.com/maps/api/geocode/json";
    /** Google trả status này khi địa chỉ hợp lệ nhưng không khớp địa điểm nào. */
    private static final String ZERO_RESULTS = "ZERO_RESULTS";

    private final GoogleMapsProperties properties;
    private final RestClient restClient;

    public GoogleGeocodingService(GoogleMapsProperties properties, RestTemplateBuilder builder) {
        this.properties = properties;
        this.restClient = RestClient.builder(builder
                        .setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                        .setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                        .build())
                .build();
    }

    @Override
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    @Override
    public Optional<GeoPoint> geocode(String address) {
        if (!isEnabled() || !StringUtils.hasText(address)) {
            return Optional.empty();
        }
        return call(UriComponentsBuilder.fromUriString(GEOCODE_URL)
                .queryParam("address", address)
                .queryParam("region", properties.getRegion())
                .queryParam("language", properties.getLanguage())
                .queryParam("key", properties.getApiKey())
                .build(false)
                .toUriString(), "geocode \"" + address + "\"");
    }

    @Override
    public Optional<GeoPoint> geocodeByPlaceId(String placeId) {
        if (!isEnabled() || !StringUtils.hasText(placeId)) {
            return Optional.empty();
        }
        return call(UriComponentsBuilder.fromUriString(GEOCODE_URL)
                .queryParam("place_id", placeId)
                .queryParam("language", properties.getLanguage())
                .queryParam("key", properties.getApiKey())
                .build(false)
                .toUriString(), "geocode place_id " + placeId);
    }

    @Override
    public Optional<GeoPoint> reverseGeocode(BigDecimal latitude, BigDecimal longitude) {
        if (!isEnabled() || latitude == null || longitude == null) {
            return Optional.empty();
        }
        return call(UriComponentsBuilder.fromUriString(GEOCODE_URL)
                .queryParam("latlng", latitude.toPlainString() + "," + longitude.toPlainString())
                .queryParam("language", properties.getLanguage())
                .queryParam("key", properties.getApiKey())
                .build(false)
                .toUriString(), "reverse geocode " + latitude + "," + longitude);
    }

    /**
     * Gọi Google và bóc kết quả đầu tiên. Mọi lỗi (mạng, quota, JSON lạ) đều nuốt
     * và trả empty — xem javadoc {@link GeocodingService} về nguyên tắc fail-soft.
     */
    private Optional<GeoPoint> call(String uri, String what) {
        try {
            JsonNode body = restClient.get().uri(uri).retrieve().body(JsonNode.class);
            if (body == null) {
                log.warn("Google Maps trả body rỗng khi {}", what);
                return Optional.empty();
            }
            String status = body.path("status").asText();
            if (!"OK".equals(status)) {
                // ZERO_RESULTS là kết quả nghiệp vụ bình thường (địa chỉ gõ sai/quá chung
                // chung), không phải sự cố hệ thống -> log ở mức debug để khỏi nhiễu.
                if (ZERO_RESULTS.equals(status)) {
                    log.debug("Google Maps không tìm thấy kết quả khi {}", what);
                } else {
                    log.warn("Google Maps lỗi khi {}: status={} error={}",
                            what, status, body.path("error_message").asText(""));
                }
                return Optional.empty();
            }
            JsonNode first = body.path("results").path(0);
            JsonNode location = first.path("geometry").path("location");
            if (!location.hasNonNull("lat") || !location.hasNonNull("lng")) {
                log.warn("Google Maps trả kết quả thiếu toạ độ khi {}", what);
                return Optional.empty();
            }
            return Optional.of(new GeoPoint(
                    BigDecimal.valueOf(location.get("lat").asDouble()),
                    BigDecimal.valueOf(location.get("lng").asDouble()),
                    emptyToNull(first.path("formatted_address").asText(null)),
                    emptyToNull(first.path("place_id").asText(null)),
                    // V59: nằm trong geometry, KHÔNG phải cạnh formatted_address.
                    emptyToNull(first.path("geometry").path("location_type").asText(null))));
        } catch (Exception e) {
            log.warn("Không gọi được Google Maps khi {}: {}", what, e.getMessage());
            return Optional.empty();
        }
    }

    private static String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }
}
