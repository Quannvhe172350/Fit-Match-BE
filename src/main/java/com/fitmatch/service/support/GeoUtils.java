package com.fitmatch.service.support;

import java.math.BigDecimal;

/**
 * Toán học toạ độ dùng chung cho tìm kiếm theo bán kính (UC-18).
 *
 * <p>Truy vấn SQL lọc hai tầng: bounding box (dùng được index (latitude, longitude))
 * rồi mới haversine chính xác. {@link #latDelta}/{@link #lngDelta} sinh hộp đó.
 */
public final class GeoUtils {

    /** Bán kính trung bình Trái Đất (km) — cùng hằng số dùng trong SQL haversine. */
    public static final double EARTH_RADIUS_KM = 6371.0;

    /**
     * Số km trên một độ vĩ, suy ra TỪ CHÍNH {@link #EARTH_RADIUS_KM}. Không dùng
     * hằng số tra bảng (111.32 km/độ theo ellipsoid WGS84): nó lớn hơn giá trị của
     * mô hình cầu mà haversine đang dùng, khiến bounding box hẹp hơn hình tròn và
     * loại nhầm những điểm nằm sát mép bán kính.
     */
    private static final double KM_PER_LAT_DEGREE = EARTH_RADIUS_KM * Math.PI / 180.0;

    /** Nới hộp 0.1% để sai số dấu phẩy động không cắt mất điểm nằm đúng trên mép. */
    private static final double BOX_SAFETY_FACTOR = 1.001;

    private GeoUtils() {
    }

    public static boolean isValidLatitude(BigDecimal latitude) {
        return latitude != null
                && latitude.doubleValue() >= -90 && latitude.doubleValue() <= 90;
    }

    public static boolean isValidLongitude(BigDecimal longitude) {
        return longitude != null
                && longitude.doubleValue() >= -180 && longitude.doubleValue() <= 180;
    }

    /** Nửa chiều cao bounding box theo độ vĩ — hằng số vì kinh tuyến cách đều. */
    public static double latDelta(double radiusKm) {
        return radiusKm * BOX_SAFETY_FACTOR / KM_PER_LAT_DEGREE;
    }

    /**
     * Nửa chiều rộng bounding box theo độ kinh — phụ thuộc vĩ độ vì các đường
     * kinh tuyến hội tụ về cực. Ở gần cực cos(lat) tiến về 0 nên chặn dưới để
     * không sinh delta vô hạn (khi đó lấy trọn 180 độ, haversine lọc phần thừa).
     */
    public static double lngDelta(double latitude, double radiusKm) {
        double scale = Math.cos(Math.toRadians(latitude));
        if (scale < 1e-6) {
            return 180.0;
        }
        return Math.min(180.0, radiusKm * BOX_SAFETY_FACTOR / (KM_PER_LAT_DEGREE * scale));
    }

    /** Khoảng cách great-circle (km) giữa hai điểm — đối chiếu với công thức trong SQL. */
    public static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
