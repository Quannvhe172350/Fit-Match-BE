package com.fitmatch.service.support;

import com.fitmatch.service.GeocodingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Quyết định toạ độ sẽ lưu cho một hồ sơ gym / chi nhánh (UC-18).
 *
 * <p>Thứ tự ưu tiên:
 * <ol>
 *   <li>Toạ độ do client gửi lên (operator ghim trực tiếp trên bản đồ hoặc chọn
 *       gợi ý Places Autocomplete) — chính xác nhất, không gọi Google lần nữa.</li>
 *   <li>Geocode chuỗi địa chỉ ghép từ address + district + city + "Việt Nam".</li>
 *   <li>Giữ nguyên toạ độ cũ nếu địa chỉ không đổi — tránh đốt quota mỗi lần
 *       operator sửa số điện thoại.</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AddressGeocoder {

    private final GeocodingService geocodingService;

    /**
     * Địa điểm client đã ghim sẵn: toạ độ kèm metadata của chính gợi ý Google mà
     * operator đã chọn. Mọi field đều nullable — client chỉ gõ tay thì rỗng hết.
     *
     * <p>{@code placeId}/{@code formattedAddress} đi cùng toạ độ chứ không phải
     * thứ suy ra sau: FE đã cầm sẵn chúng trong kết quả Places Autocomplete, gửi
     * kèm thì không phải geocode lại chỉ để lấy đúng hai giá trị đó.
     */
    /**
     * @param pinnedByUser V60 — toạ độ này do người kéo ghim trên bản đồ chứ không
     *                     phải lấy nguyên từ gợi ý Places. Đánh dấu để job làm mới
     *                     định kỳ không kéo ghim về chỗ Google nói.
     */
    public record Pin(BigDecimal latitude, BigDecimal longitude,
                      String placeId, String formattedAddress, boolean pinnedByUser) {

        public static Pin none() {
            return new Pin(null, null, null, null, false);
        }

        boolean hasValidCoordinates() {
            return GeoUtils.isValidLatitude(latitude) && GeoUtils.isValidLongitude(longitude);
        }
    }

    /**
     * Kết quả phân giải. {@code resolved} = false nghĩa là không xác định được toạ
     * độ mới; caller giữ nguyên giá trị đang có thay vì ghi đè null.
     *
     * <p>{@code formattedAddress}/{@code placeId} vẫn có thể null ngay cả khi
     * {@code resolved} = true (client ghim toạ độ mà không gửi kèm metadata) —
     * caller phải hiểu null ở đây là "không có thông tin mới", KHÔNG phải "hãy xoá".
     */
    public record Resolution(boolean resolved, BigDecimal latitude, BigDecimal longitude,
                             String formattedAddress, String placeId, String locationType,
                             boolean pinnedByUser, LocalDateTime geocodedAt) {

        public static Resolution none() {
            return new Resolution(false, null, null, null, null, null, false, null);
        }

        /** Kết quả geocode = Google đoán, nên {@code pinnedByUser} luôn false. */
        public static Resolution of(GeoPoint point) {
            return new Resolution(true, point.latitude(), point.longitude(),
                    point.formattedAddress(), point.placeId(), point.locationType(),
                    false, LocalDateTime.now());
        }
    }

    /**
     * Kết quả geocode có kém tới mức phải bắt gym xác minh lại địa chỉ không (V59)?
     *
     * <p>Google phân bốn mức: {@code ROOFTOP} (đúng toà nhà), {@code RANGE_INTERPOLATED}
     * (nội suy giữa hai số nhà), {@code GEOMETRIC_CENTER} (giữa một đoạn phố) và
     * {@code APPROXIMATE}. Ba mức đầu đều đủ để khách đi tới đúng nơi; chỉ
     * APPROXIMATE là đáng lo — nó thường là tâm phường/quận, nghĩa là Google
     * không hiểu được số nhà và ghim có thể lệch hàng km.
     *
     * <p>{@code locationType} null (operator tự ghim trên bản đồ) KHÔNG bị coi là
     * kém: người ở đó biết vị trí thật rõ hơn dịch vụ đoán địa chỉ.
     */
    public static boolean isImprecise(String locationType) {
        return "APPROXIMATE".equals(locationType);
    }

    /** @param pin địa điểm client ghim sẵn; {@link Pin#none()} khi không có. */
    public Resolution resolve(Pin pin, String address, String district, String city) {
        if (pin.hasValidCoordinates()) {
            // locationType null: đây là toạ độ người thật ghim, không phải phỏng
            // đoán của Google — không có "độ chính xác" nào để chấm điểm.
            return new Resolution(true, pin.latitude(), pin.longitude(),
                    emptyToNull(pin.formattedAddress()), emptyToNull(pin.placeId()),
                    null, pin.pinnedByUser(), LocalDateTime.now());
        }
        String query = buildQuery(address, district, city);
        if (!StringUtils.hasText(query)) {
            return Resolution.none();
        }
        Optional<GeoPoint> point = geocodingService.geocode(query);
        if (point.isEmpty()) {
            log.debug("Không phân giải được toạ độ cho địa chỉ \"{}\"", query);
            return Resolution.none();
        }
        return Resolution.of(point.get());
    }

    /**
     * Ghép các mảnh địa chỉ thành một chuỗi Google hiểu được. Thêm "Việt Nam" để
     * chặn kết quả trùng tên ở nước khác ngay cả khi param region bị bỏ qua.
     */
    public static String buildQuery(String... parts) {
        String joined = Stream.of(parts)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
        return joined.isEmpty() ? "" : joined + ", Việt Nam";
    }

    private static String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }
}
