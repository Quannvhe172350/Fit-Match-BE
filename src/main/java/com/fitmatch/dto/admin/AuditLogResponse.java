package com.fitmatch.dto.admin;

import com.fitmatch.entity.AuditLog;
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
public class AuditLogResponse {

    private Long id;
    private String action;
    private String targetType;
    private String targetId;
    private String description;
    private String actor;
    private LocalDateTime timestamp;

    public static AuditLogResponse of(AuditLog log) {
        return AuditLogResponse.builder()
                .id(log.getId())
                .action(log.getAction())
                .targetType(log.getTargetType())
                .targetId(log.getTargetId())
                .description(log.getDescription())
                .actor(log.getCreatedBy())
                .timestamp(log.getCreatedAt())
                .build();
    }
}
