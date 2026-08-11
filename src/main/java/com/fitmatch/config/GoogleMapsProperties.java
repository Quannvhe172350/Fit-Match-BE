package com.fitmatch.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Cấu hình Google Maps Platform (UC-18 — tìm gym theo bán kính).
 *
 * <p>API key ở đây là key PHÍA SERVER (Geocoding API), khác với key phía trình
 * duyệt của FE (Maps JavaScript API + Places). Key server nên bị giới hạn theo
 * IP; key browser giới hạn theo HTTP referrer.
 *
 * <p>Không có key -> {@code enabled} = false: toàn bộ luồng geocode tự động tắt
 * một cách im lặng (fail-soft), gym vẫn lưu được địa chỉ, chỉ là không có toạ độ.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.google-maps")
public class GoogleMapsProperties {

    /** Server key dùng cho Geocoding API. Rỗng = tắt geocoding. */
    private String apiKey;

    /** Bias kết quả về Việt Nam — "Nguyễn Trãi" ở VN khác "Nguyễn Trãi" nơi khác. */
    private String region = "vn";

    /** Ngôn ngữ của formatted_address trả về. */
    private String language = "vi";

    /** Timeout gọi Google (ms) — geocode nằm trong request của operator nên phải ngắn. */
    private int connectTimeoutMs = 3000;
    private int readTimeoutMs = 5000;

    /**
     * Mở endpoint proxy geocode công khai (/api/marketplace/geocode) cho FE khi FE
     * KHÔNG có key Maps JavaScript riêng. Mặc định tắt: proxy công khai tiêu quota
     * Google của nền tảng và là bề mặt lạm dụng. Bật thì có rate limit theo IP.
     */
    private boolean publicGeocodeEnabled = false;

    /** Bán kính tối đa cho phép tìm kiếm (km) — chặn quét toàn quốc bằng radius khổng lồ. */
    private double maxSearchRadiusKm = 50;

    /** Bán kính mặc định khi client chỉ gửi toạ độ mà không gửi radius. */
    private double defaultSearchRadiusKm = 5;

    /**
     * V59 — số ngày một kết quả geocode còn được coi là dùng lại được. Dài quá thì
     * gym chuyển địa điểm vẫn trả toạ độ cũ; ngắn quá thì đệm gần như vô dụng.
     */
    private int cacheTtlDays = 90;

    /** V59 — bật job nền tự bổ sung / làm mới toạ độ. Tắt = chỉ chạy tay qua endpoint admin. */
    private boolean backfillJobEnabled = false;

    /**
     * Lịch chạy job. {@code @Scheduled} đọc thẳng từ Environment nên field này
     * không bắt buộc — để ở đây cho cả khối cấu hình nằm cùng một chỗ.
     */
    private String backfillJobCron = "0 30 3 * * *";

    /** Trần số bản ghi job xử lý mỗi lần chạy, cho MỖI pha (bổ sung và làm mới). */
    private int backfillJobLimit = 100;

    /**
     * Toạ độ cũ hơn ngần này ngày sẽ được tra lại theo place_id. 0 = tắt hẳn việc
     * làm mới, chỉ bổ sung hồ sơ còn thiếu toạ độ.
     */
    private int refreshAfterDays = 180;

    public boolean isEnabled() {
        return StringUtils.hasText(apiKey);
    }
}
