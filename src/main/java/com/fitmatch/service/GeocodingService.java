package com.fitmatch.service;

import com.fitmatch.service.support.GeoPoint;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Ánh xạ địa chỉ &lt;-&gt; toạ độ qua Google Geocoding API (UC-18).
 *
 * <p>Mọi phương thức đều fail-soft: lỗi mạng/quota/địa chỉ không tìm thấy đều
 * trả {@link Optional#empty()} chứ không ném ngoại lệ — geocode chỉ là phần làm
 * giàu dữ liệu, không được phép làm hỏng thao tác lưu hồ sơ gym.
 */
public interface GeocodingService {

    /** Có cấu hình API key hay không — controller/admin dùng để báo trạng thái. */
    boolean isEnabled();

    /** Địa chỉ -> toạ độ. Empty khi tắt geocoding, địa chỉ rỗng, hoặc Google không khớp. */
    Optional<GeoPoint> geocode(String address);

    /** Toạ độ -> địa chỉ đã chuẩn hoá (dùng cho nút "Vị trí của tôi"). */
    Optional<GeoPoint> reverseGeocode(BigDecimal latitude, BigDecimal longitude);
}
