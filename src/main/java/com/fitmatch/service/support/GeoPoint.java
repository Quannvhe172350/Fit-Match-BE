package com.fitmatch.service.support;

import java.math.BigDecimal;

/**
 * Một toạ độ đã phân giải (UC-18). {@code formattedAddress} và {@code placeId}
 * là null khi toạ độ do người dùng tự chọn trên bản đồ thay vì geocode từ địa chỉ.
 */
public record GeoPoint(BigDecimal latitude, BigDecimal longitude,
                       String formattedAddress, String placeId) {

    public static GeoPoint of(double latitude, double longitude) {
        return new GeoPoint(BigDecimal.valueOf(latitude), BigDecimal.valueOf(longitude), null, null);
    }
}
