package com.fitmatch.service.support;

import java.math.BigDecimal;

/**
 * Một toạ độ đã phân giải (UC-18). {@code formattedAddress} và {@code placeId}
 * là null khi toạ độ do người dùng tự chọn trên bản đồ thay vì geocode từ địa chỉ.
 *
 * @param locationType độ chính xác Google báo về (V59) — xem
 *                     {@link AddressGeocoder#isImprecise(String)}. Null khi
 *                     không phải kết quả geocode.
 */
public record GeoPoint(BigDecimal latitude, BigDecimal longitude,
                       String formattedAddress, String placeId, String locationType) {

    public static GeoPoint of(double latitude, double longitude) {
        return new GeoPoint(BigDecimal.valueOf(latitude), BigDecimal.valueOf(longitude),
                null, null, null);
    }
}
