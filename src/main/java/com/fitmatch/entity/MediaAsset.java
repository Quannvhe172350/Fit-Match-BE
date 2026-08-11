package com.fitmatch.entity;

import com.fitmatch.common.enums.MediaEntityType;
import com.fitmatch.common.enums.MediaImageType;
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

/**
 * Metadata của một file ảnh đã upload lên object storage (V64).
 *
 * <p>Bảng này KHÔNG chứa binary: nội dung ảnh nằm trên Google Cloud Storage, DB chỉ
 * giữ {@code storageKey} + {@code bucketName} để dựng lại URL (public hoặc signed).
 * Quan hệ tới entity nghiệp vụ là polymorphic ({@code entityType} + {@code entityId})
 * nên một bảng phục vụ được avatar user, ảnh gym/chi nhánh, ảnh review, ảnh check-in...
 *
 * <p>{@code entityId} null = ảnh "nháp": đã upload nhưng chưa gắn vào bản ghi nào
 * (ví dụ khách chọn ảnh trước khi bấm Gửi đánh giá). Ảnh nháp chỉ chủ sở hữu thấy và
 * được dọn định kỳ bởi {@code OrphanMediaCleanupJob}.
 */
@Entity
@Table(name = "media_assets", indexes = {
        @Index(name = "idx_media_entity", columnList = "entity_type,entity_id,image_type,sort_order"),
        @Index(name = "idx_media_owner", columnList = "owner_user_id"),
        @Index(name = "idx_media_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MediaAsset extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 20)
    private MediaEntityType entityType;

    /** Null = ảnh nháp chưa gắn vào entity nào. */
    @Column(name = "entity_id")
    private Long entityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "image_type", nullable = false, length = 20)
    private MediaImageType imageType;

    /** Object key trên storage — KHÔNG bao giờ chứa tên file do client gửi lên. */
    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    @Column(name = "bucket_name", length = 255)
    private String bucketName;

    /** Object key của bản thu nhỏ; null nếu không sinh được (vd ảnh WebP). */
    @Column(name = "thumbnail_key", length = 500)
    private String thumbnailKey;

    /** Tên file gốc — chỉ để hiển thị/tải về, không dùng dựng path. */
    @Column(name = "original_name", length = 255)
    private String originalName;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    /**
     * URL công khai đã tính sẵn — chỉ có giá trị khi bucket cho phép đọc ẩn danh
     * ({@code app.media.public-read=true}). Bucket private thì để null và URL được
     * ký lại mỗi lần trả response.
     */
    @Column(name = "url", length = 1000)
    private String url;

    /** Chú thích hiển thị dưới ảnh (kế thừa từ gym_media cũ). */
    @Column(name = "caption", length = 255)
    private String caption;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    @Column(name = "is_primary", nullable = false)
    @Builder.Default
    private boolean primary = false;

    /**
     * Người upload — dùng để chặn việc gắn ảnh của người khác vào review/entity của
     * mình. Tách khỏi {@code createdBy} (username, audit) vì username có thể đổi.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private User owner;
}
