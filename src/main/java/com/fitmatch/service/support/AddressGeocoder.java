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
     * Kết quả phân giải. {@code resolved} = false nghĩa là không xác định được toạ
     * độ mới; caller giữ nguyên giá trị đang có thay vì ghi đè null.
     */
    public record Resolution(boolean resolved, BigDecimal latitude, BigDecimal longitude,
                             String formattedAddress, String placeId, LocalDateTime geocodedAt) {

        public static Resolution none() {
            return new Resolution(false, null, null, null, null, null);
        }

        public static Resolution of(GeoPoint point) {
            return new Resolution(true, point.latitude(), point.longitude(),
                    point.formattedAddress(), point.placeId(), LocalDateTime.now());
        }
    }

    /**
     * @param explicitLat toạ độ client gửi kèm (nullable)
     * @param explicitLng toạ độ client gửi kèm (nullable)
     */
    public Resolution resolve(BigDecimal explicitLat, BigDecimal explicitLng,
                              String address, String district, String city) {
        if (GeoUtils.isValidLatitude(explicitLat) && GeoUtils.isValidLongitude(explicitLng)) {
            return new Resolution(true, explicitLat, explicitLng, null, null, LocalDateTime.now());
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
}
