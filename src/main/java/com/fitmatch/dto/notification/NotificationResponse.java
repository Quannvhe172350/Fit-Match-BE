package com.fitmatch.dto.notification;

import com.fitmatch.common.enums.NotificationCategory;
import com.fitmatch.entity.Notification;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/** Thông báo in-app (UC-075). */
@Getter
@Builder
public class NotificationResponse {

    private Long id;
    private NotificationCategory category;
    private String title;
    private String body;
    private String link;
    private boolean read;
    private LocalDateTime createdAt;

    public static NotificationResponse of(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .category(n.getCategory())
                .title(n.getTitle())
                .body(n.getBody())
                .link(n.getLink())
                .read(n.getReadAt() != null)
                .createdAt(n.getCreatedAt())
                .build();
    }
}
