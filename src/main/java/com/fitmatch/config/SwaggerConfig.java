package com.fitmatch.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI fitMatchOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("FitMatch API")
                        .description("""
                                REST API cho nền tảng marketplace đặt lịch PT (Personal Trainer) & Gym.

                                **Xác thực:** hầu hết endpoint yêu cầu JWT. Gọi `POST /api/auth/login`,
                                lấy `accessToken` rồi bấm **Authorize** và dán token (không cần tiền tố `Bearer`).
                                """)
                        .version("v1.0")
                        .contact(new Contact().name("FitMatch Team")))
                // Khai báo security scheme JWT để Swagger UI hiển thị nút Authorize.
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Dán JWT access token (lấy từ /api/auth/login).")))
                // Áp dụng mặc định cho mọi endpoint; endpoint public sẽ override bằng @SecurityRequirements rỗng nếu cần.
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
