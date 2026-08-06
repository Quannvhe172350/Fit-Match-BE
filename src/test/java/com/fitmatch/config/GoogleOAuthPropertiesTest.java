package com.fitmatch.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleOAuthPropertiesTest {

    private GoogleOAuthProperties bind(String clientIds) {
        var source = new MapConfigurationPropertySource(Map.of("app.google-oauth.client-ids", clientIds));
        return new Binder(source).bind("app.google-oauth", GoogleOAuthProperties.class)
                .orElseGet(GoogleOAuthProperties::new);
    }

    @Test
    void emptyEnvVar_disablesGoogleSignIn() {
        // application.yml có `${GOOGLE_OAUTH_CLIENT_IDS:}` — không set env là bind chuỗi
        // rỗng. Phải ra "tắt tính năng", không được làm hỏng khởi động ứng dụng.
        assertThat(bind("").isEnabled()).isFalse();
        assertThat(bind("  ").isEnabled()).isFalse();
    }

    @Test
    void commaSeparatedList_bindsEveryClientId() {
        GoogleOAuthProperties props = bind("web-id.apps.googleusercontent.com, android-id.apps.googleusercontent.com");

        assertThat(props.isEnabled()).isTrue();
        assertThat(props.getEffectiveClientIds())
                .containsExactly("web-id.apps.googleusercontent.com", "android-id.apps.googleusercontent.com");
    }
}
