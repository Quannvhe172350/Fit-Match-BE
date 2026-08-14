package com.fitmatch.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fitmatch.common.enums.GeocodingProvider;
import com.fitmatch.config.GeocodingProperties;
import com.fitmatch.service.GeocodingService;
import com.fitmatch.service.support.GeoPoint;
import com.fitmatch.service.support.GeoSuggestion;
import com.fitmatch.service.support.LocationPrecision;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Triển khai {@link GeocodingService} trên Nominatim (UC-18).
 *
 * <p>CẢNH BÁO VẬN HÀNH: chỉ chọn provider này khi trỏ {@code app.geocoding.nominatim.base-url}
 * vào một instance TỰ DỰNG. Máy chủ công cộng của OpenStreetMap giới hạn tuyệt
 * đối 1 lượt/giây, script chạy định kỳ chỉ được 4 lượt/phút, và cấm hẳn geocode
 * hàng loạt — job backfill sẽ vi phạm ngay lần chạy đầu tiên.
 *
 * <p>Có mặt ở đây vì hai lý do: chạy dev/CI không cần đăng ký khoá, và là đường
 * lui nếu muốn tự chủ hoàn toàn dữ liệu về sau.
 */
@Slf4j
@Service
public class NominatimGeocodingService implements GeocodingService {

    private final GeocodingProperties properties;
    private final RestClient restClient;

    public NominatimGeocodingService(GeocodingProperties properties, RestTemplateBuilder builder) {
        this.properties = properties;
        this.restClient = RestClient.builder(builder
                        .setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                        .setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                        .build())
                // Chính sách Nominatim BẮT BUỘC định danh ứng dụng; thiếu header này
                // là bị chặn chứ không phải bị giảm tốc.
                .defaultHeader(HttpHeaders.USER_AGENT, properties.getNominatim().getUserAgent())
                .build();
    }

    private String baseUrl() {
        return properties.getNominatim().getBaseUrl();
    }

    @Override
    public boolean isEnabled() {
        return StringUtils.hasText(baseUrl());
    }

    @Override
    public Optional<GeoPoint> geocode(String address) {
        if (!isEnabled() || !StringUtils.hasText(address)) {
            return Optional.empty();
        }
        return first(UriComponentsBuilder.fromUriString(baseUrl() + "/search")
                .queryParam("q", address)
                .queryParam("countrycodes", properties.getRegion())
                .queryParam("accept-language", properties.getLanguage())
                .queryParam("addressdetails", 1)
                .queryParam("limit", 1)
                .queryParam("format", "jsonv2")
                .build(false)
                .toUriString(), "geocode \"" + address + "\"");
    }

    @Override
    public Optional<GeoPoint> geocodeByPlaceId(String placeId) {
        if (!isEnabled() || !StringUtils.hasText(placeId)) {
            return Optional.empty();
        }
        // /lookup nhận osm_type + osm_id (ví dụ "N123456"), KHÔNG nhận place_id nội
        // bộ của Nominatim — place_id đó không ổn định giữa các lần tái dựng chỉ mục.
        // Vì vậy giá trị lưu ở cột place_id cho provider này chính là osm_ids.
        return first(UriComponentsBuilder.fromUriString(baseUrl() + "/lookup")
                .queryParam("osm_ids", placeId)
                .queryParam("accept-language", properties.getLanguage())
                .queryParam("addressdetails", 1)
                .queryParam("format", "jsonv2")
                .build(false)
                .toUriString(), "lookup " + placeId);
    }

    @Override
    public Optional<GeoSuggestion> reverseGeocode(BigDecimal latitude, BigDecimal longitude) {
        if (!isEnabled() || latitude == null || longitude == null) {
            return Optional.empty();
        }
        // toSuggestion chứ không phải toPoint: chiều ngược cần cả quận/huyện và
        // tỉnh/thành tách rời để form địa chỉ điền thẳng vào hai ô riêng.
        return singleSuggestion(UriComponentsBuilder.fromUriString(baseUrl() + "/reverse")
                .queryParam("lat", latitude.toPlainString())
                .queryParam("lon", longitude.toPlainString())
                .queryParam("accept-language", properties.getLanguage())
                .queryParam("addressdetails", 1)
                .queryParam("format", "jsonv2")
                .build(false)
                .toUriString(), "reverse geocode " + latitude + "," + longitude);
    }

    /**
     * Nominatim không có endpoint gợi ý riêng — dùng chính /search với limit lớn hơn.
     *
     * <p>Chất lượng gợi ý vì thế kém hơn hẳn Geoapify hay Photon: /search được
     * thiết kế cho chuỗi địa chỉ đầy đủ, không phải cho chuỗi gõ dở.
     */
    @Override
    public List<GeoSuggestion> autocomplete(String query, int limit) {
        if (!isEnabled() || !StringUtils.hasText(query)) {
            return List.of();
        }
        Optional<JsonNode> body = request(UriComponentsBuilder.fromUriString(baseUrl() + "/search")
                .queryParam("q", query)
                .queryParam("countrycodes", properties.getRegion())
                .queryParam("accept-language", properties.getLanguage())
                .queryParam("addressdetails", 1)
                .queryParam("limit", limit)
                .queryParam("format", "jsonv2")
                .build(false)
                .toUriString(), "autocomplete \"" + query + "\"");
        if (body.isEmpty()) {
            return List.of();
        }
        List<GeoSuggestion> suggestions = new ArrayList<>();
        for (JsonNode result : body.get()) {
            toSuggestion(result).ifPresent(suggestions::add);
        }
        return suggestions;
    }

    private Optional<GeoSuggestion> toSuggestion(JsonNode result) {
        return toPoint(result).map(point -> {
            JsonNode address = result.path("address");
            String display = point.formattedAddress();
            // display_name của Nominatim rất dài (tới tận tên nước); lấy đoạn đầu
            // làm nhãn ngắn để ô nhập không bị tràn.
            String label = display == null ? null : display.split(",")[0].trim();
            return new GeoSuggestion(
                    StringUtils.hasText(label) ? label : display,
                    display,
                    point.latitude(), point.longitude(),
                    point.placeId(), GeocodingProvider.NOMINATIM,
                    // V66: phường/xã, KHÔNG phải quận/huyện — xem javadoc GeoSuggestion#ward.
                    firstNonEmpty(address, "suburb", "quarter", "city_district"),
                    firstNonEmpty(address, "state", "city"));
        });
    }

    private static String firstNonEmpty(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = emptyToNull(node.path(field).asText(null));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /** /search và /lookup trả về MẢNG. */
    private Optional<GeoPoint> first(String uri, String what) {
        return request(uri, what).map(body -> body.path(0)).flatMap(this::toPoint);
    }

    /** /reverse trả về MỘT object, không bọc trong mảng. */
    private Optional<GeoSuggestion> singleSuggestion(String uri, String what) {
        return request(uri, what).flatMap(this::toSuggestion);
    }

    private Optional<JsonNode> request(String uri, String what) {
        try {
            JsonNode body = restClient.get().uri(uri).retrieve().body(JsonNode.class);
            if (body == null) {
                log.warn("Nominatim trả body rỗng khi {}", what);
                return Optional.empty();
            }
            return Optional.of(body);
        } catch (Exception e) {
            log.warn("Không gọi được Nominatim khi {}: {}", what, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<GeoPoint> toPoint(JsonNode result) {
        // lat/lon là CHUỖI trong response Nominatim, không phải số.
        String lat = result.path("lat").asText(null);
        String lon = result.path("lon").asText(null);
        if (!StringUtils.hasText(lat) || !StringUtils.hasText(lon)) {
            return Optional.empty();
        }
        LocationPrecision precision = LocationPrecision.fromNominatim(result.path("addresstype").asText(null));
        String osmType = result.path("osm_type").asText("");
        String osmId = result.path("osm_id").asText(null);
        try {
            return Optional.of(new GeoPoint(
                    new BigDecimal(lat), new BigDecimal(lon),
                    emptyToNull(result.path("display_name").asText(null)),
                    osmLookupId(osmType, osmId),
                    precision == null ? null : precision.storedValue(),
                    GeocodingProvider.NOMINATIM));
        } catch (NumberFormatException e) {
            log.warn("Nominatim trả toạ độ không phải số: lat={} lon={}", lat, lon);
            return Optional.empty();
        }
    }

    /**
     * Ghép định danh /lookup hiểu được: chữ cái đầu của osm_type + osm_id
     * (node -> "N", way -> "W", relation -> "R").
     */
    private static String osmLookupId(String osmType, String osmId) {
        if (!StringUtils.hasText(osmType) || !StringUtils.hasText(osmId)) {
            return null;
        }
        return osmType.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + osmId;
    }

    private static String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }
}
