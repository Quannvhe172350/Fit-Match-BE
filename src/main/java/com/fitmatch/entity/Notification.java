package com.fitmatch.entity;

import com.fitmatch.common.enums.NotificationCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Thông báo in-app gửi tới một user (UC-075). readAt null = chưa đọc.
 * Schema: notifications(id, user_id FK, category, title, body, link, read_at, + audit).
 */
@Entity
@Table(name = "notifications", indexes =
        @Index(name = "idx_notifications_user", columnList = "user_id,read_at"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationCategory category;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 1000)
    private String body;

    /** Đường dẫn FE liên quan (vd /profile/bookings, /gym/disputes). */
    @Column(length = 300)
    private String link;

    @Column(name = "read_at")
    private LocalDateTime readAt;
}
