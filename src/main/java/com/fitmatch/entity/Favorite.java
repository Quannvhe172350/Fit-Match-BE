package com.fitmatch.entity;

import com.fitmatch.common.enums.FavoriteType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Mục yêu thích của Customer (UC-15..21). Một dòng = (user, type, targetId).
 * Ràng buộc UNIQUE(user_id, type, target_id) để tránh trùng.
 * Schema: favorites(id, user_id FK, type, target_id, + audit).
 */
@Entity
@Table(name = "favorites", uniqueConstraints =
        @UniqueConstraint(name = "uk_favorite_user_type_target", columnNames = {"user_id", "type", "target_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Favorite extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FavoriteType type;

    @Column(name = "target_id", nullable = false)
    private Long targetId;
}
