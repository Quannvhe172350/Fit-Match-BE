package com.fitmatch.dto.admin;

import com.fitmatch.entity.NotificationTemplate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** UC-075: DTO cho quản trị template thông báo. */
public final class NotificationTemplateDtos {

    private NotificationTemplateDtos() {
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TemplateResponse {
        private String code;
        private String title;
        private String body;
        private boolean enabled;
        private String placeholders;
        private String updatedBy;
        private LocalDateTime updatedAt;

        public static TemplateResponse of(NotificationTemplate t) {
            return TemplateResponse.builder()
                    .code(t.getCode())
                    .title(t.getTitle())
                    .body(t.getBody())
                    .enabled(t.isEnabled())
                    .placeholders(t.getPlaceholders())
                    .updatedBy(t.getUpdatedBy())
                    .updatedAt(t.getUpdatedAt())
                    .build();
        }
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TemplateUpdateRequest {
        @NotBlank(message = "Title is required")
        @Size(max = 200)
        private String title;

        @NotBlank(message = "Body is required")
        @Size(max = 1000)
        private String body;

        @NotNull(message = "Enabled flag is required")
        private Boolean enabled;
    }
}
