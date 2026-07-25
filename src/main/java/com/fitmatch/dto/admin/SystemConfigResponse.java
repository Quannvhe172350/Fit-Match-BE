package com.fitmatch.dto.admin;

import com.fitmatch.entity.SystemConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemConfigResponse {

    private String key;
    private String value;
    private String description;
    private String updatedBy;
    private LocalDateTime updatedAt;

    public static SystemConfigResponse of(SystemConfig c) {
        return SystemConfigResponse.builder()
                .key(c.getConfigKey())
                .value(c.getConfigValue())
                .description(c.getDescription())
                .updatedBy(c.getUpdatedBy())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
