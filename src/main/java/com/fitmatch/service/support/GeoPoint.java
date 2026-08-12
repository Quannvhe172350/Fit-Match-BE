package com.fitmatch.service.support;

import com.fitmatch.common.enums.GeocodingProvider;

import java.math.BigDecimal;

/**
 * Một toạ độ đã phân giải (UC-18). {@code formattedAddress} và {@code placeId}
 * là null khi toạ độ do người dùng tự chọn trên bản đồ thay vì geocode từ địa chỉ.
 *
 * @param locationType độ chính xác đã chuẩn hoá — luôn là tên một hằng số của
 *                     {@link LocationPrecision}, bất kể provider nào sinh ra.
 *                     Null khi không phải kết quả geocode.
 * @param provider     dịch vụ đã cấp {@code placeId}. Phải đi kèm để job làm mới
 *                     không tra một id của dịch vụ này trên dịch vụ khác.
 */
public record GeoPoint(BigDecimal latitude, BigDecimal longitude,
                       String formattedAddress, String placeId, String locationType,
                       GeocodingProvider provider) {

    public static GeoPoint of(double latitude, double longitude) {
        return new GeoPoint(BigDecimal.valueOf(latitude), BigDecimal.valueOf(longitude),
                null, null, null, null);
    }
}
