package com.fitmatch.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI fitMatchOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("FitMatch API")
                        .description("FitMatch REST API documentation")
                        .version("v1.0"));
    }
}
