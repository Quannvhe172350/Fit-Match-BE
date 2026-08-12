package com.fitmatch.common.enums;

/**
 * Dịch vụ đã sinh ra một toạ độ / place_id (UC-18).
 *
 * <p>Được LƯU cùng bản ghi chứ không chỉ dùng lúc chạy: {@code place_id} của ba
 * nhà cung cấp là ba không gian định danh khác nhau, nên job làm mới định kỳ
 * phải biết ai đã cấp cái id đó. Tra một id của Google trên Geoapify sẽ không
 * báo lỗi — nó trả về một địa điểm khác hẳn và lặng lẽ dời ghim phòng gym đi
 * nơi khác.
 */
public enum GeocodingProvider {

    /** Google Geocoding API — chất lượng địa chỉ Việt Nam tốt nhất, tính tiền theo lượt. */
    GOOGLE,

    /** Geoapify — dữ liệu OpenStreetMap, 3.000 lượt/ngày miễn phí, không cần thẻ. */
    GEOAPIFY,

    /**
     * Nominatim — máy chủ OpenStreetMap. Bản công cộng giới hạn 1 lượt/giây và
     * CẤM dùng cho tải lớn, nên chỉ chọn khi tự dựng instance riêng.
     */
    NOMINATIM
}
