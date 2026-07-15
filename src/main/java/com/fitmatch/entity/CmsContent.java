package com.fitmatch.entity;

import com.fitmatch.common.enums.CmsType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Nội dung CMS công khai & chiến dịch nổi bật (UC-074). Chỉ mục published mới
 * hiển thị public; sortOrder tăng dần.
 * Schema: cms_contents(id, type, title, body, image_url, link, sort_order,
 * published, + audit).
 */
@Entity
@Table(name = "cms_contents", indexes =
        @Index(name = "idx_cms_type_pub", columnList = "type,published,sort_order"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CmsContent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CmsType type;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(length = 500)
    private String link;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    @Column(nullable = false)
    @Builder.Default
    private boolean published = false;
}
