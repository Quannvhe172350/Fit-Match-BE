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
 * Triển khai {@link GeocodingService} trên Geoapify (UC-18).
 *
 * <p>Dữ liệu nền là OpenStreetMap nhưng đi qua hạ tầng của Geoapify, nên KHÔNG
 * dính giới hạn 1 lượt/giây của máy chủ Nominatim công cộng: gói miễn phí cho
 * 3.000 lượt/ngày ở 5 lượt/giây, không cần thẻ tín dụng, và cho phép lưu lại kết
 * quả — điều kiện bắt buộc để bảng {@code geocode_cache} hợp lệ về mặt điều khoản.
 *
 * <p>Đánh đổi cần biết: độ phủ số nhà của OSM ở Việt Nam mỏng hơn Google. Vì thế
 * bản đồ ghim tay ở form địa chỉ không còn là tiện ích phụ mà là đường lui chính
 * thức khi kết quả trả về ở mức {@code APPROXIMATE}.
 *
 * <p>Response được đọc qua {@link JsonNode} thay vì bind vào DTO cứng — cùng lý
 * do như bản Google: nhà cung cấp thêm field mới thường xuyên.
 */
@Slf4j
@Service
public class GeoapifyGeocodingService implements GeocodingService {

    private static final String SEARCH_URL = "https://api.geoapify.com/v1/geocode/search";
    private static final String REVERSE_URL = "https://api.geoapify.com/v1/geocode/reverse";
    private static final String DETAILS_URL = "https://api.geoapify.com/v2/place-details";
    private static final String AUTOCOMPLETE_URL = "https://api.geoapify.com/v1/geocode/autocomplete";

    private final GeocodingProperties properties;
    private final RestClient restClient;

    public GeoapifyGeocodingService(GeocodingProperties properties, RestTemplateBuilder builder) {
        this.properties = properties;
        this.restClient = RestClient.builder(builder
                        .setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                        .setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                        .build())
                .build();
    }

    private String apiKey() {
        return properties.getGeoapify().getApiKey();
    }

    @Override
    public boolean isEnabled() {
        return StringUtils.hasText(apiKey());
    }

    @Override
    public Optional<GeoPoint> geocode(String address) {
        if (!isEnabled() || !StringUtils.hasText(address)) {
            return Optional.empty();
        }
        return call(UriComponentsBuilder.fromUriString(SEARCH_URL)
                .queryParam("text", address)
                // filter countrycode chứ không phải "bias": bias chỉ ưu tiên, vẫn
                // trả về địa điểm nước khác khi chuỗi khớp tốt hơn ở đó.
                .queryParam("filter", "countrycode:" + properties.getRegion())
                .queryParam("lang", properties.getLanguage())
                .queryParam("limit", 1)
                .queryParam("format", "json")
                .queryParam("apiKey", apiKey())
                .build(false)
                .toUriString(), "geocode \"" + address + "\"");
    }

    @Override
    public Optional<GeoPoint> geocodeByPlaceId(String placeId) {
        if (!isEnabled() || !StringUtils.hasText(placeId)) {
            return Optional.empty();
        }
        // Place Details trả GeoJSON (features[]) chứ không phải mảng results[] như
        // /geocode/search, nên đi qua nhánh bóc tách riêng.
        return callFeatures(UriComponentsBuilder.fromUriString(DETAILS_URL)
                .queryParam("id", placeId)
                .queryParam("lang", properties.getLanguage())
                .queryParam("apiKey", apiKey())
                .build(false)
                .toUriString(), "geocode place_id " + placeId);
    }

    @Override
    public Optional<GeoSuggestion> reverseGeocode(BigDecimal latitude, BigDecimal longitude) {
        if (!isEnabled() || latitude == null || longitude == null) {
            return Optional.empty();
        }
        // callSuggestion chứ không phải call: chiều ngược cần cả quận/huyện và
        // tỉnh/thành tách rời để form địa chỉ điền thẳng vào hai ô riêng.
        return callSuggestion(UriComponentsBuilder.fromUriString(REVERSE_URL)
                .queryParam("lat", latitude.toPlainString())
                .queryParam("lon", longitude.toPlainString())
                .queryParam("lang", properties.getLanguage())
                .queryParam("limit", 1)
                .queryParam("format", "json")
                .queryParam("apiKey", apiKey())
                .build(false)
                .toUriString(), "reverse geocode " + latitude + "," + longitude);
    }

    @Override
    public List<GeoSuggestion> autocomplete(String query, int limit) {
        if (!isEnabled() || !StringUtils.hasText(query)) {
            return List.of();
        }
        Optional<JsonNode> body = request(UriComponentsBuilder.fromUriString(AUTOCOMPLETE_URL)
                .queryParam("text", query)
                .queryParam("filter", "countrycode:" + properties.getRegion())
                .queryParam("lang", properties.getLanguage())
                .queryParam("limit", limit)
                .queryParam("format", "json")
                .queryParam("apiKey", apiKey())
                .build(false)
                .toUriString(), "autocomplete \"" + query + "\"");
        if (body.isEmpty()) {
            return List.of();
        }
        List<GeoSuggestion> suggestions = new ArrayList<>();
        for (JsonNode result : body.get().path("results")) {
            toSuggestion(result).ifPresent(suggestions::add);
        }
        return suggestions;
    }

    private Optional<GeoSuggestion> toSuggestion(JsonNode result) {
        if (!result.hasNonNull("lat") || !result.hasNonNull("lon")) {
            return Optional.empty();
        }
        String formatted = emptyToNull(result.path("formatted").asText(null));
        // address_line1 là phần "tên + số nhà" — ngắn hơn hẳn chuỗi đầy đủ và là
        // thứ hợp lý để hiện lại trong ô nhập sau khi người dùng chọn.
        String label = emptyToNull(result.path("address_line1").asText(null));
        return Optional.of(new GeoSuggestion(
                label != null ? label : formatted,
                formatted,
                BigDecimal.valueOf(result.get("lat").asDouble()),
                BigDecimal.valueOf(result.get("lon").asDouble()),
                emptyToNull(result.path("place_id").asText(null)),
                GeocodingProvider.GEOAPIFY,
                wardOf(result),
                cityOf(result)));
    }

    /**
     * Phường/xã theo cách chia hiện hành của Việt Nam (V66).
     *
     * <p>{@code suburb} là field mang phường — kiểm chứng trên dữ liệu thật:
     * "Phường Hoàn Kiếm", "Phường Cầu Giấy", "Phường Gia Định", "Phường Hải Châu".
     *
     * <p>KHÔNG dùng {@code county}: đó là cấp huyện đã bị bỏ từ đợt sắp xếp đơn vị
     * hành chính 2025, và dữ liệu còn sót lại thì sai — cùng bộ điểm trên,
     * {@code county} trả "Hoàn Kiếm" cho một điểm ở Cầu Giấy và "Thanh Khê" cho
     * một điểm ở Hải Châu. Trước V66 nó được thử ĐẦU TIÊN nên form luôn nhận đúng
     * giá trị sai này.
     *
     * <p>{@code district} tụt xuống cuối vì ở VN nó thường mang tên khu phố ("Khu
     * phố 43", "Dịch Vọng Hậu") chứ không phải một cấp hành chính.
     */
    private static String wardOf(JsonNode result) {
        return firstNonEmpty(result, "suburb", "quarter", "district");
    }

    /**
     * Tỉnh/thành. Các thành phố trực thuộc trung ương (Hà Nội, TP.HCM) nằm ở
     * admin_level 4 nên rơi vào {@code state}, KHÔNG phải {@code city}.
     */
    private static String cityOf(JsonNode result) {
        return firstNonEmpty(result, "state", "city");
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

    /** Nhánh {@code format=json}: kết quả nằm phẳng trong mảng {@code results}. */
    private Optional<GeoPoint> call(String uri, String what) {
        return request(uri, what).map(body -> body.path("results").path(0)).flatMap(this::toPoint);
    }

    /** Như {@link #call} nhưng giữ lại quận/huyện + tỉnh/thành (chiều ngược). */
    private Optional<GeoSuggestion> callSuggestion(String uri, String what) {
        return request(uri, what).map(body -> body.path("results").path(0)).flatMap(this::toSuggestion);
    }

    /** Nhánh GeoJSON: toạ độ và thuộc tính tách làm hai nhánh của {@code features[0]}. */
    private Optional<GeoPoint> callFeatures(String uri, String what) {
        return request(uri, what)
                .map(body -> body.path("features").path(0).path("properties"))
                .flatMap(this::toPoint);
    }

    /**
     * Gọi Geoapify. Mọi lỗi (mạng, hết quota, JSON lạ) đều nuốt và trả empty —
     * xem javadoc {@link GeocodingService} về nguyên tắc fail-soft.
     */
    private Optional<JsonNode> request(String uri, String what) {
        try {
            JsonNode body = restClient.get().uri(uri).retrieve().body(JsonNode.class);
            if (body == null) {
                log.warn("Geoapify trả body rỗng khi {}", what);
                return Optional.empty();
            }
            return Optional.of(body);
        } catch (Exception e) {
            // Hết hạn mức ngày trả 429; đáng chú ý hơn lỗi mạng thường nên log riêng.
            log.warn("Không gọi được Geoapify khi {}: {}", what, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<GeoPoint> toPoint(JsonNode result) {
        if (!result.hasNonNull("lat") || !result.hasNonNull("lon")) {
            // Không tìm thấy địa điểm nào là kết quả nghiệp vụ bình thường (địa chỉ
            // gõ sai / quá chung chung), không phải sự cố hệ thống.
            return Optional.empty();
        }
        Double confidence = result.path("rank").hasNonNull("confidence")
                ? result.path("rank").get("confidence").asDouble()
                : null;
        LocationPrecision precision = LocationPrecision.fromGeoapify(
                result.path("result_type").asText(null), confidence);
        return Optional.of(new GeoPoint(
                BigDecimal.valueOf(result.get("lat").asDouble()),
                BigDecimal.valueOf(result.get("lon").asDouble()),
                emptyToNull(result.path("formatted").asText(null)),
                emptyToNull(result.path("place_id").asText(null)),
                precision == null ? null : precision.storedValue(),
                GeocodingProvider.GEOAPIFY));
    }

    private static String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }
}
