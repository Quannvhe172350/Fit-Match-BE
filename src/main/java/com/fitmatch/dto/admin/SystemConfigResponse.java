package com.fitmatch.dto.admin;

import com.fitmatch.entity.SystemConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemConfigResponse {

    private Long id;
    private String configKey;
    private String configValue;
    private String description;

    public static SystemConfigResponse of(SystemConfig c) {
        return SystemConfigResponse.builder()
                .id(c.getId())
                .configKey(c.getConfigKey())
                .configValue(c.getConfigValue())
                .description(c.getDescription())
                .build();
    }
}
