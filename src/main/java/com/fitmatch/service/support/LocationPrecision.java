package com.fitmatch.service.support;

/**
 * Độ chính xác của một kết quả geocode, đã chuẩn hoá về MỘT từ vựng chung (UC-18).
 *
 * <p>Mỗi nhà cung cấp mô tả độ chính xác một kiểu: Google trả {@code location_type}
 * dạng chuỗi, Geoapify trả {@code rank.confidence} dạng số 0–1 kèm
 * {@code result_type}, Nominatim thì chỉ có {@code addresstype} và {@code class}.
 * Nếu để nguyên, mọi nơi đọc giá trị này đều phải biết provider nào đang chạy —
 * và {@link AddressQuality} sẽ lặng lẽ ngừng gắn cờ khi đổi provider.
 *
 * <p>TÊN HẰNG SỐ giữ nguyên từ vựng của Google, không phải vì Google là chuẩn mà
 * vì cột {@code location_type} đang chứa sẵn đúng bốn chuỗi này ở dữ liệu cũ.
 * Đổi tên đồng nghĩa với một migration dữ liệu không đem lại giá trị chức năng
 * nào — cái giá đó không đáng.
 */
public enum LocationPrecision {

    /** Đúng toà nhà / số nhà. */
    ROOFTOP,

    /** Nội suy giữa hai số nhà đã biết trên cùng đoạn phố. */
    RANGE_INTERPOLATED,

    /** Tâm hình học của một đoạn phố hoặc một thửa đất. */
    GEOMETRIC_CENTER,

    /**
     * Chỉ khớp tới tâm phường/quận/thành phố — ghim có thể lệch hàng km. Đây là
     * mức DUY NHẤT bị {@link AddressQuality} coi là cần xác minh lại.
     */
    APPROXIMATE;

    /** Giá trị đem đi lưu vào cột {@code location_type}. */
    public String storedValue() {
        return name();
    }

    /**
     * Ánh xạ {@code location_type} của Google. Chuỗi lạ -> null ("không rõ") chứ
     * KHÔNG mặc định về APPROXIMATE: đoán bừa là kém chính xác sẽ bắt một phòng
     * gym bình thường phải đi xác minh lại địa chỉ mà không có căn cứ.
     */
    public static LocationPrecision fromGoogle(String locationType) {
        if (locationType == null) {
            return null;
        }
        try {
            return valueOf(locationType.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Ánh xạ tín hiệu của Geoapify.
     *
     * <p>Geoapify cho hai thứ: {@code result_type} (tầng dữ liệu khớp được) và
     * {@code rank.confidence} (0–1). Ưu tiên {@code result_type} vì nó nói thẳng
     * đã khớp tới cấp hành chính nào; confidence chỉ dùng để hạ cấp một kết quả
     * mang danh "building" nhưng thực chất rất mơ hồ.
     *
     * @param resultType {@code result_type} trong response; null khi thiếu
     * @param confidence {@code rank.confidence}; null khi thiếu
     */
    public static LocationPrecision fromGeoapify(String resultType, Double confidence) {
        String type = resultType == null ? "" : resultType.trim().toLowerCase(java.util.Locale.ROOT);
        LocationPrecision byType = switch (type) {
            case "building", "amenity" -> ROOFTOP;
            case "street" -> GEOMETRIC_CENTER;
            case "postcode", "suburb", "district", "city", "county", "state", "country" -> APPROXIMATE;
            default -> null;
        };
        // Confidence thấp thì kết quả "đúng toà nhà" cũng không đáng tin — Geoapify
        // vẫn gán result_type=building cho những khớp rất lỏng.
        if (byType == ROOFTOP && confidence != null && confidence < 0.5) {
            return GEOMETRIC_CENTER;
        }
        if (byType != null) {
            return byType;
        }
        if (confidence == null) {
            return null;
        }
        if (confidence >= 0.9) return ROOFTOP;
        if (confidence >= 0.5) return GEOMETRIC_CENTER;
        return APPROXIMATE;
    }

    /**
     * Ánh xạ {@code addresstype} của Nominatim.
     *
     * <p>Nominatim không công bố điểm tin cậy nào dùng được cho việc này —
     * {@code importance} là độ nổi tiếng của địa điểm, không phải độ chính xác
     * của phép khớp. Nên chỉ dựa vào cấp dữ liệu đã khớp.
     */
    public static LocationPrecision fromNominatim(String addressType) {
        String type = addressType == null ? "" : addressType.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (type) {
            case "building", "house", "amenity", "shop", "leisure" -> ROOFTOP;
            case "road", "street" -> GEOMETRIC_CENTER;
            case "postcode", "suburb", "quarter", "neighbourhood", "village", "town",
                 "city", "county", "state", "province", "country" -> APPROXIMATE;
            default -> null;
        };
    }
}
