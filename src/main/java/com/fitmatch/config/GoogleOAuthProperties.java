package com.fitmatch.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Cấu hình đăng nhập bằng Google (UC-003 — Google Identity Services).
 *
 * <p>FE lấy ID token từ Google rồi POST {@code /api/auth/google}; BE chỉ cần
 * OAuth Client ID để kiểm tra claim {@code aud} của token — KHÔNG cần client
 * secret vì đây không phải luồng authorization code.
 *
 * <p>Không cấu hình client id -> {@code enabled} = false: endpoint trả 503 và
 * FE tự ẩn nút Google, phần còn lại của hệ thống không bị ảnh hưởng.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.google-oauth")
public class GoogleOAuthProperties {

    /**
     * Các OAuth Client ID được chấp nhận làm audience của ID token. Nhiều giá trị
     * (phân tách bằng dấu phẩy) để một BE phục vụ nhiều client — web, Android, iOS.
     */
    private List<String> clientIds = new ArrayList<>();

    /** Client id sau khi bỏ phần tử rỗng — env rỗng bind ra list có một chuỗi trắng. */
    public List<String> getEffectiveClientIds() {
        return clientIds.stream().filter(StringUtils::hasText).map(String::trim).toList();
    }

    public boolean isEnabled() {
        return !getEffectiveClientIds().isEmpty();
    }
}
