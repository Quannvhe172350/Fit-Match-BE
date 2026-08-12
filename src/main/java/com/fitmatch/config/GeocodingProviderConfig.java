package com.fitmatch.config;

import com.fitmatch.service.GeocodingService;
import com.fitmatch.service.impl.GeoapifyGeocodingService;
import com.fitmatch.service.impl.GoogleGeocodingService;
import com.fitmatch.service.impl.NominatimGeocodingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chọn nhà cung cấp geocoding theo {@code app.geocoding.provider} (UC-18).
 *
 * <p>Tồn tại vì {@code CachingGeocodingService} không thể inject
 * {@code GeocodingService} một cách trần trụi: chính nó CŨNG là một
 * {@code GeocodingService} (và còn là {@code @Primary}), nên Spring sẽ nối nó
 * vào chính nó. Trước đây vấn đề được né bằng cách inject thẳng kiểu cụ thể
 * {@code GoogleGeocodingService} — cách đó chỉ hoạt động khi có đúng một
 * implementation.
 *
 * <p>Bean này là mắt xích được đệm bọc bên ngoài, KHÔNG phải bean mà phần còn
 * lại của ứng dụng dùng. Mọi chỗ khác inject {@code GeocodingService} vẫn nhận
 * {@code CachingGeocodingService}.
 */
@Slf4j
@Configuration
public class GeocodingProviderConfig {

    /** Tên qualifier — trùng với giá trị ở constructor của lớp đệm. */
    public static final String DELEGATE = "geocodingDelegate";

    @Bean(DELEGATE)
    public GeocodingService geocodingDelegate(GeocodingProperties properties,
                                              GoogleGeocodingService google,
                                              GeoapifyGeocodingService geoapify,
                                              NominatimGeocodingService nominatim) {
        GeocodingService selected = switch (properties.getProvider()) {
            case GOOGLE -> google;
            case GEOAPIFY -> geoapify;
            case NOMINATIM -> nominatim;
        };
        if (selected.isEnabled()) {
            log.info("Geocoding provider: {}", properties.getProvider());
        } else {
            // Không ném ngoại lệ: toàn bộ luồng geocode vốn đã fail-soft, gym vẫn
            // lưu được địa chỉ mà không có toạ độ. Nhưng thiếu khoá là lỗi cấu hình
            // thật sự nên phải kêu to, đừng để nó im lặng biến mất.
            log.warn("Geocoding provider {} chưa được cấu hình (thiếu khoá/base-url) — "
                    + "mọi thao tác geocode sẽ bị bỏ qua và gym sẽ không có toạ độ.",
                    properties.getProvider());
        }
        return selected;
    }
}
