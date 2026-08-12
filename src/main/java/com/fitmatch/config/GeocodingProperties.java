package com.fitmatch.config;

import com.fitmatch.common.enums.GeocodingProvider;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Cấu hình ánh xạ địa chỉ &lt;-&gt; toạ độ (UC-18 — tìm gym theo bán kính).
 *
 * <p>Thay cho {@code GoogleMapsProperties}: từ V65 hệ thống chạy được trên nhiều
 * nhà cung cấp, nên các tham số CHUNG (bán kính, TTL đệm, job nền) không còn chỗ
 * đứng dưới một tiền tố mang tên Google. Khoá riêng của từng dịch vụ nằm trong
 * khối con tương ứng.
 *
 * <p>Lưu ý: đây là cấu hình PHÍA SERVER. Bản đồ của FE dùng Leaflet + tile
 * OpenStreetMap và không cần khoá nào cả.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.geocoding")
public class GeocodingProperties {

    /** Dịch vụ đang dùng. Đổi giá trị này là đổi toàn bộ luồng geocode. */
    private GeocodingProvider provider = GeocodingProvider.GEOAPIFY;

    /** Bias kết quả về Việt Nam — "Nguyễn Trãi" ở VN khác "Nguyễn Trãi" nơi khác. */
    private String region = "vn";

    /** Ngôn ngữ của địa chỉ trả về. */
    private String language = "vi";

    /** Timeout gọi dịch vụ (ms) — geocode nằm trong request của operator nên phải ngắn. */
    private int connectTimeoutMs = 3000;
    private int readTimeoutMs = 5000;

    /**
     * Mở endpoint proxy geocode công khai (/api/marketplace/geocode) cho FE.
     *
     * <p>Từ khi bản đồ chuyển sang Leaflet, FE KHÔNG còn khoá geocode phía trình
     * duyệt nào nữa — proxy này là đường duy nhất để ô "tìm quanh đây" hoạt động,
     * nên mặc định BẬT. Vẫn có rate limit theo IP ở {@code AuthRateLimitFilter}.
     */
    private boolean publicGeocodeEnabled = true;

    /** Bán kính tối đa cho phép tìm kiếm (km) — chặn quét toàn quốc bằng radius khổng lồ. */
    private double maxSearchRadiusKm = 50;

    /** Bán kính mặc định khi client chỉ gửi toạ độ mà không gửi radius. */
    private double defaultSearchRadiusKm = 5;

    /**
     * Số ngày một kết quả geocode còn được coi là dùng lại được. Dài quá thì gym
     * chuyển địa điểm vẫn trả toạ độ cũ; ngắn quá thì đệm gần như vô dụng.
     */
    private int cacheTtlDays = 90;

    /** Bật job nền tự bổ sung / làm mới toạ độ. Tắt = chỉ chạy tay qua endpoint admin. */
    private boolean backfillJobEnabled = false;

    /**
     * Lịch chạy job. {@code @Scheduled} đọc thẳng từ Environment nên field này
     * không bắt buộc — để ở đây cho cả khối cấu hình nằm cùng một chỗ.
     */
    private String backfillJobCron = "0 30 3 * * *";

    /**
     * Trần số bản ghi job xử lý mỗi lần chạy, cho MỖI pha (bổ sung và làm mới).
     *
     * <p>Giữ số này thấp khi chạy trên gói miễn phí: hạn mức của Geoapify tính
     * theo NGÀY và không cộng dồn, nên một đợt backfill lớn có thể ăn hết quota
     * của cả ngày hôm đó dù cả tháng dùng rất ít.
     */
    private int backfillJobLimit = 100;

    /**
     * Toạ độ cũ hơn ngần này ngày sẽ được tra lại theo place_id. 0 = tắt hẳn việc
     * làm mới, chỉ bổ sung hồ sơ còn thiếu toạ độ.
     */
    private int refreshAfterDays = 180;

    private final Google google = new Google();
    private final Geoapify geoapify = new Geoapify();
    private final Nominatim nominatim = new Nominatim();

    @Getter
    @Setter
    public static class Google {
        /** Server key cho Geocoding API. Rỗng = không chọn được provider GOOGLE. */
        private String apiKey;
    }

    @Getter
    @Setter
    public static class Geoapify {
        /** API key lấy ở my.geoapify.com — gói miễn phí không cần thẻ tín dụng. */
        private String apiKey;
    }

    @Getter
    @Setter
    public static class Nominatim {
        /**
         * Đổi sang instance TỰ DỰNG khi chạy thật.
         *
         * <p>Máy chủ công cộng của OpenStreetMap giới hạn tuyệt đối 1 lượt/giây,
         * và script chạy định kỳ chỉ được 4 lượt/phút — job backfill sẽ vi phạm
         * chính sách ngay lần chạy đầu tiên.
         */
        private String baseUrl = "https://nominatim.openstreetmap.org";

        /**
         * Chính sách của Nominatim BẮT BUỘC định danh ứng dụng qua User-Agent.
         * Thiếu hoặc dùng giá trị chung chung sẽ bị chặn.
         */
        private String userAgent = "FitMatch/1.0 (gym marketplace)";
    }
}
