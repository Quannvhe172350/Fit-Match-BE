package com.fitmatch.config;

import com.fitmatch.repository.GeocodeCacheRepository;
import com.fitmatch.service.GeocodingService;
import com.fitmatch.service.impl.CachingGeocodingService;
import com.fitmatch.service.impl.GeoapifyGeocodingService;
import com.fitmatch.service.impl.GoogleGeocodingService;
import com.fitmatch.service.impl.NominatimGeocodingService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * V65 (UC-18): dây nối bean của tầng geocoding.
 *
 * <p>Vùng này KHÔNG được unit test mock-based bảo vệ: có tới năm bean cùng kiểu
 * {@code GeocodingService} (ba provider, lớp đệm {@code @Primary}, và bean
 * delegate), nên một sai sót về qualifier chỉ lộ ra lúc ứng dụng khởi động —
 * hoặc tệ hơn, lộ ra dưới dạng lớp đệm tự bọc chính nó và đệ quy vô hạn.
 */
class GeocodingProviderConfigTest {

    /**
     * Chỉ dựng đúng phần liên quan tới geocoding. Nạp cả ứng dụng sẽ kéo theo
     * Flyway + JPA và đòi một MariaDB đang chạy.
     */
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(GeocodingProperties.class)
    @Import({GeocodingProviderConfig.class,
            GoogleGeocodingService.class,
            GeoapifyGeocodingService.class,
            NominatimGeocodingService.class,
            CachingGeocodingService.class})
    static class Wiring {

        @Bean
        RestTemplateBuilder restTemplateBuilder() {
            return new RestTemplateBuilder();
        }

        @Bean
        GeocodeCacheRepository geocodeCacheRepository() {
            return mock(GeocodeCacheRepository.class);
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations
                    .of(PropertyPlaceholderAutoConfiguration.class))
            .withUserConfiguration(Wiring.class);

    /**
     * Điểm mấu chốt: chỗ nào inject {@code GeocodingService} cũng phải nhận lớp
     * ĐỆM, không phải provider trần — nếu không, mỗi lần lưu hồ sơ gym lại là một
     * lượt gọi tính tiền cho câu trả lời đã có sẵn trong DB.
     */
    @Test
    void primaryBean_isTheCachingWrapper() {
        runner.withPropertyValues("app.geocoding.geoapify.api-key=test-key")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(GeocodingService.class))
                            .isInstanceOf(CachingGeocodingService.class);
                });
    }

    @Test
    void providerProperty_selectsTheDelegate() {
        runner.withPropertyValues("app.geocoding.provider=GOOGLE",
                        "app.geocoding.google.api-key=test-key")
                .run(context -> assertThat(context.getBean(GeocodingProviderConfig.DELEGATE))
                        .isInstanceOf(GoogleGeocodingService.class));

        runner.withPropertyValues("app.geocoding.provider=NOMINATIM")
                .run(context -> assertThat(context.getBean(GeocodingProviderConfig.DELEGATE))
                        .isInstanceOf(NominatimGeocodingService.class));
    }

    /** Mặc định phải là Geoapify — provider miễn phí, không cần thẻ tín dụng. */
    @Test
    void defaultProvider_isGeoapify() {
        runner.run(context -> {
            assertThat(context.getBean(GeocodingProperties.class).getProvider())
                    .isEqualTo(com.fitmatch.common.enums.GeocodingProvider.GEOAPIFY);
            assertThat(context.getBean(GeocodingProviderConfig.DELEGATE))
                    .isInstanceOf(GeoapifyGeocodingService.class);
        });
    }

    /**
     * Thiếu khoá KHÔNG được làm hỏng khởi động: toàn bộ luồng geocode vốn fail-soft,
     * gym vẫn phải lưu được địa chỉ (chỉ là không có toạ độ).
     */
    @Test
    void missingApiKey_startsUpDisabledInsteadOfCrashing() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(GeocodingService.class).isEnabled()).isFalse();
        });
    }

    /** Lớp đệm phải bọc provider, tuyệt đối không bọc chính nó. */
    @Test
    void cachingWrapper_doesNotWrapItself() {
        runner.withPropertyValues("app.geocoding.geoapify.api-key=test-key")
                .run(context -> assertThat(context.getBean(GeocodingProviderConfig.DELEGATE))
                        .isNotInstanceOf(CachingGeocodingService.class));
    }
}
