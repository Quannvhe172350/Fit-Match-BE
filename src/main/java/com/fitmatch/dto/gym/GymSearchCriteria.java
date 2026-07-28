package com.fitmatch.dto.gym;

import com.fitmatch.service.support.GeoUtils;

import java.math.BigDecimal;

/**
 * Bộ tiêu chí tìm gym trên marketplace (UC-18).
 *
 * <p>Hai chế độ loại trừ nhau ở tầng truy vấn:
 * <ul>
 *   <li>{@link #hasLocation()} = false — lọc theo text/giá bằng JPA Specification,
 *       sắp xếp theo {@code Pageable.sort} (mới nhất / đánh giá / tên).</li>
 *   <li>{@link #hasLocation()} = true — tìm theo bán kính bằng truy vấn native,
 *       LUÔN sắp xếp theo khoảng cách tăng dần; sort của client bị bỏ qua vì
 *       "gần tôi nhất" chính là thứ tự người dùng vừa yêu cầu.</li>
 * </ul>
 *
 * @param radiusKm bán kính (km); null = dùng mặc định của cấu hình
 */
public record GymSearchCriteria(String keyword, String city, String district,
                                BigDecimal minPrice, BigDecimal maxPrice,
                                BigDecimal latitude, BigDecimal longitude, Double radiusKm) {

    /** Có đủ toạ độ hợp lệ để chạy nhánh tìm theo bán kính hay không. */
    public boolean hasLocation() {
        return GeoUtils.isValidLatitude(latitude) && GeoUtils.isValidLongitude(longitude);
    }
}
