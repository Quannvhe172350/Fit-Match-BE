package com.fitmatch.repository;

import com.fitmatch.common.enums.MediaEntityType;
import com.fitmatch.common.enums.MediaImageType;
import com.fitmatch.entity.MediaAsset;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface MediaAssetRepository extends JpaRepository<MediaAsset, Long> {

    List<MediaAsset> findByEntityTypeAndEntityIdOrderBySortOrderAscIdAsc(
            MediaEntityType entityType, Long entityId);

    List<MediaAsset> findByEntityTypeAndEntityIdAndImageTypeOrderBySortOrderAscIdAsc(
            MediaEntityType entityType, Long entityId, MediaImageType imageType);

    Page<MediaAsset> findByEntityTypeAndEntityIdOrderBySortOrderAscIdAsc(
            MediaEntityType entityType, Long entityId, Pageable pageable);

    Page<MediaAsset> findByEntityTypeAndEntityIdAndImageTypeOrderBySortOrderAscIdAsc(
            MediaEntityType entityType, Long entityId, MediaImageType imageType, Pageable pageable);

    Optional<MediaAsset> findByIdAndOwner_Username(Long id, String username);

    /** Ảnh của nhiều entity cùng loại — dùng để nạp gallery cho một trang danh sách (tránh N+1). */
    List<MediaAsset> findByEntityTypeAndEntityIdInOrderBySortOrderAscIdAsc(
            MediaEntityType entityType, Collection<Long> entityIds);

    long countByEntityTypeAndEntityIdAndImageType(
            MediaEntityType entityType, Long entityId, MediaImageType imageType);

    @Query("select coalesce(max(m.sortOrder), -1) from MediaAsset m "
            + "where m.entityType = :type and m.entityId = :id and m.imageType = :imageType")
    int maxSortOrder(@Param("type") MediaEntityType type,
                     @Param("id") Long id,
                     @Param("imageType") MediaImageType imageType);

    /**
     * Ảnh nháp quá hạn (chưa gắn vào entity nào) — job dọn rác gọi để xoá cả object
     * trên GCS lẫn bản ghi, tránh tích tụ file mồ côi khi người dùng bỏ dở form.
     */
    List<MediaAsset> findByEntityIdIsNullAndCreatedAtBefore(LocalDateTime cutoff, Pageable pageable);
}
