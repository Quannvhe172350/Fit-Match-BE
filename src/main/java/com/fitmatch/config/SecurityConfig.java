package com.fitmatch.config;

import com.fitmatch.security.AuthRateLimitFilter;
import com.fitmatch.security.JwtAuthFilter;
import com.fitmatch.security.RestAccessDeniedHandler;
import com.fitmatch.security.RestAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final AuthRateLimitFilter authRateLimitFilter;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    private final RestAccessDeniedHandler restAccessDeniedHandler;

    @Value("${cors.allowed-origins:*}")
    private List<String> allowedOrigins;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization"));
        // P1-9: KHÔNG cho phép credentials khi origin là wildcard "*" — nếu không
        // Spring sẽ reflect mọi Origin kèm credentials. Auth dùng Bearer header
        // (không cookie) nên tắt credentials ở chế độ wildcard là an toàn; cấu hình
        // origin cụ thể qua CORS_ALLOWED_ORIGINS thì mới bật credentials.
        boolean wildcard = allowedOrigins.contains("*");
        config.setAllowCredentials(!wildcard);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/refresh",
                                "/api/auth/verify-email",
                                "/api/auth/resend-verification",
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password",
                                "/api/health",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Webhook đối soát Casso — xác thực bằng Secure-Token header trong controller.
                        .requestMatchers(HttpMethod.POST, "/api/webhooks/**").permitAll()
                        // P1-8: chỉ avatar và chứng chỉ PT là công khai; tài liệu KYC
                        // (giấy phép kinh doanh/định danh trong folder documents) yêu cầu
                        // đăng nhập — không để lộ cho khách vãng lai qua URL.
                        .requestMatchers(HttpMethod.GET, "/api/files/avatars/**",
                                "/api/files/certifications/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/files/documents/**").authenticated()
                        // Marketplace browse & public content là công khai (Guest/Customer).
                        .requestMatchers(HttpMethod.GET, "/api/marketplace/**", "/api/public/**").permitAll()
                        .anyRequest().authenticated()
                )
                // UC-003: 401 trả mã cụ thể (TOKEN_EXPIRED/TOKEN_INVALID), 403 chuẩn JSON.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(restAuthenticationEntryPoint)
                        .accessDeniedHandler(restAccessDeniedHandler))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                // Rate limit các endpoint auth trước khi vào xử lý JWT.
                .addFilterBefore(authRateLimitFilter, JwtAuthFilter.class);

        return http.build();
    }
}
