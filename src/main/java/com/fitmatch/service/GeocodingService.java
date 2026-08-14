package com.fitmatch.service;

import com.fitmatch.service.support.GeoPoint;
import com.fitmatch.service.support.GeoSuggestion;

import java.math.BigDecimal;
import java.util.List;
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

    /**
     * place_id -> toạ độ hiện tại của CHÍNH địa điểm đó (V59).
     *
     * <p>Dùng cho job làm mới toạ độ định kỳ: geocode lại chuỗi địa chỉ có thể ra
     * một địa điểm khác hẳn (Google đổi cách hiểu chuỗi chữ, hoặc địa chỉ mơ hồ),
     * qua đó dời ghim của phòng gym đi nơi khác. Tra theo place_id thì địa điểm
     * luôn là địa điểm cũ, chỉ toạ độ được cập nhật.
     */
    Optional<GeoPoint> geocodeByPlaceId(String placeId);

    /**
     * Toạ độ -> địa chỉ đã chuẩn hoá (nút "Vị trí của tôi", và ghim tay ở form
     * địa chỉ gym).
     *
     * <p>Trả {@link GeoSuggestion} chứ không phải {@link GeoPoint} vì đây là chiều
     * DUY NHẤT mà caller cần các mảnh hành chính tách rời: form địa chỉ điền ngược
     * quận/huyện + tỉnh/thành vào hai ô riêng ngay khi operator thả ghim. Chiều
     * xuôi không cần — nó nhận sẵn hai giá trị đó từ chính form.
     */
    Optional<GeoSuggestion> reverseGeocode(BigDecimal latitude, BigDecimal longitude);

    /**
     * Gợi ý địa điểm khi người dùng đang gõ dở (V65) — thay cho Places Autocomplete
     * chạy phía trình duyệt.
     *
     * <p>Mặc định trả danh sách RỖNG chứ không ném ngoại lệ: không phải nhà cung
     * cấp nào cũng có API gợi ý dùng được, và ô nhập địa chỉ phía FE đã có sẵn chế
     * độ lui — gõ xong nhấn Enter để geocode nguyên chuỗi. Danh sách rỗng nghĩa là
     * "không có gợi ý", không phải "hỏng".
     *
     * @param limit trần số gợi ý; caller phải kẹp về khoảng hợp lý trước khi gọi
     */
    default List<GeoSuggestion> autocomplete(String query, int limit) {
        return List.of();
    }
}
