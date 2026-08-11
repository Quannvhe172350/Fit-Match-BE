package com.fitmatch.service.impl;

import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.MediaEntityType;
import com.fitmatch.common.enums.MediaImageType;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.config.MediaProperties;
import com.fitmatch.dto.media.MediaResponse;
import com.fitmatch.dto.media.MediaUpdateRequest;
import com.fitmatch.entity.MediaAsset;
import com.fitmatch.entity.User;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.MediaAssetRepository;
import com.fitmatch.service.MediaService;
import com.fitmatch.service.StorageService;
import com.fitmatch.service.support.ImageFileValidator;
import com.fitmatch.service.support.ImageProcessor;
import com.fitmatch.service.support.MediaAccessGuard;
import com.fitmatch.service.support.MediaUrlResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaServiceImpl implements MediaService {

    private final MediaAssetRepository mediaRepository;
    private final StorageService storageService;
    private final MediaProperties properties;
    private final ImageFileValidator validator;
    private final ImageProcessor imageProcessor;
    private final MediaAccessGuard guard;
    private final MediaUrlResolver urlResolver;

    // ------------------------------------------------------------------
    // Upload
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public List<MediaResponse> upload(String username, MediaEntityType entityType, Long entityId,
                                      MediaImageType imageType, MultipartFile[] files) {
        User owner = guard.requireUser(username);
        guard.requireValidCombination(entityType, imageType);
        guard.requireCanManage(owner, entityType, entityId);
        validator.validateBatch(files);

        if (entityId != null && !imageType.isSingleton()) {
            long existing = mediaRepository.countByEntityTypeAndEntityIdAndImageType(entityType, entityId, imageType);
            if (existing + files.length > properties.getMaxImagesPerEntity()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "This item can hold at most " + properties.getMaxImagesPerEntity()
                                + " images (already has " + existing + ")");
            }
        }

        // Ảnh AVATAR/COVER chỉ có một: thay thế thì ảnh cũ phải biến mất cả ở DB lẫn storage.
        List<MediaAsset> replaced = imageType.isSingleton() && entityId != null
                ? mediaRepository.findByEntityTypeAndEntityIdAndImageTypeOrderBySortOrderAscIdAsc(
                        entityType, entityId, imageType)
                : List.of();

        int nextSortOrder = entityId == null
                ? 0
                : mediaRepository.maxSortOrder(entityType, entityId, imageType) + 1;

        List<MediaAsset> saved = new ArrayList<>();
        for (MultipartFile file : files) {
            saved.add(storeOne(owner, entityType, entityId, imageType, file, nextSortOrder++));
        }

        if (!replaced.isEmpty()) {
            mediaRepository.deleteAll(replaced);
            replaced.forEach(this::deleteObjectsAfterCommit);
        }
        // Entity chưa có ảnh chính nào (thư viện rỗng, hoặc vừa thay ảnh singleton)
        // thì ảnh đầu tiên của lượt upload này giữ vai trò đó — FE luôn có ảnh
        // đại diện để hiển thị mà không cần thao tác thêm.
        if (entityId != null && !hasPrimary(entityType, entityId, imageType)) {
            saved.get(0).setPrimary(true);
        }
        return saved.stream().map(this::toResponse).toList();
    }

    /**
     * Đẩy một file lên storage rồi lưu metadata. Object được xoá lại nếu giao dịch
     * DB rollback (xem {@link #deleteObjectsOnRollback}) — mất một file mồ côi trên
     * bucket thì không ai thấy, nhưng để lại thì trả tiền lưu trữ mãi mãi.
     */
    private MediaAsset storeOne(User owner, MediaEntityType entityType, Long entityId,
                                MediaImageType imageType, MultipartFile file, int sortOrder) {
        String mimeType = validator.validate(file);
        byte[] content = readBytes(file);

        String uuid = UUID.randomUUID().toString();
        String directory = directoryFor(entityType, entityId, imageType, owner.getId());
        String storageKey = directory + "/" + uuid + "." + validator.extensionFor(mimeType);

        StorageService.StoredObject stored = storageService.put(storageKey, content, mimeType);
        deleteObjectsOnRollback(storageKey);

        ImageProcessor.Dimensions dimensions = imageProcessor.readDimensions(content);
        String thumbnailKey = null;
        ImageProcessor.Thumbnail thumbnail = imageProcessor.createThumbnail(content);
        if (thumbnail.bytes() != null) {
            thumbnailKey = directory + "/thumb/" + uuid + ".jpg";
            storageService.put(thumbnailKey, thumbnail.bytes(), thumbnail.mimeType());
            deleteObjectsOnRollback(thumbnailKey);
        }

        return mediaRepository.save(MediaAsset.builder()
                .entityType(entityType)
                .entityId(entityId)
                .imageType(imageType)
                .storageKey(stored.storageKey())
                .bucketName(stored.bucket())
                .thumbnailKey(thumbnailKey)
                .originalName(sanitizeName(file.getOriginalFilename()))
                .mimeType(mimeType)
                .fileSize(content.length)
                .width(dimensions.width())
                .height(dimensions.height())
                .url(stored.publicUrl())
                .sortOrder(sortOrder)
                .owner(owner)
                .build());
    }

    // ------------------------------------------------------------------
    // Đọc
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<MediaResponse> list(String username, MediaEntityType entityType, Long entityId,
                                    MediaImageType imageType) {
        guard.requireCanRead(username, entityType, entityId);
        List<MediaAsset> assets = imageType == null
                ? mediaRepository.findByEntityTypeAndEntityIdOrderBySortOrderAscIdAsc(entityType, entityId)
                : mediaRepository.findByEntityTypeAndEntityIdAndImageTypeOrderBySortOrderAscIdAsc(
                        entityType, entityId, imageType);
        return assets.stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MediaResponse> listPaged(String username, MediaEntityType entityType, Long entityId,
                                                 MediaImageType imageType, Pageable pageable) {
        guard.requireCanRead(username, entityType, entityId);
        var page = imageType == null
                ? mediaRepository.findByEntityTypeAndEntityIdOrderBySortOrderAscIdAsc(entityType, entityId, pageable)
                : mediaRepository.findByEntityTypeAndEntityIdAndImageTypeOrderBySortOrderAscIdAsc(
                        entityType, entityId, imageType, pageable);
        return PageResponse.of(page, this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, List<MediaResponse>> listForEntities(MediaEntityType entityType,
                                                          Collection<Long> entityIds,
                                                          MediaImageType imageType) {
        if (entityIds == null || entityIds.isEmpty()) return Map.of();
        return mediaRepository.findByEntityTypeAndEntityIdInOrderBySortOrderAscIdAsc(entityType, entityIds)
                .stream()
                .filter(m -> imageType == null || m.getImageType() == imageType)
                .collect(Collectors.groupingBy(MediaAsset::getEntityId, LinkedHashMap::new,
                        Collectors.mapping(this::toResponse, Collectors.toList())));
    }

    @Override
    @Transactional(readOnly = true)
    public MediaResponse primaryFor(MediaEntityType entityType, Long entityId, MediaImageType imageType) {
        if (entityId == null) return null;
        return mediaRepository
                .findByEntityTypeAndEntityIdAndImageTypeOrderBySortOrderAscIdAsc(entityType, entityId, imageType)
                .stream()
                .min(Comparator.comparing((MediaAsset m) -> !m.isPrimary())
                        .thenComparingInt(MediaAsset::getSortOrder))
                .map(this::toResponse)
                .orElse(null);
    }

    // ------------------------------------------------------------------
    // Sửa / xoá
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public MediaResponse update(String username, Long mediaId, MediaUpdateRequest request) {
        User user = guard.requireUser(username);
        MediaAsset media = requireManageable(user, mediaId);
        if (request.getCaption() != null) media.setCaption(request.getCaption());
        if (request.getSortOrder() != null) media.setSortOrder(request.getSortOrder());
        if (Boolean.TRUE.equals(request.getPrimary())) {
            clearPrimaryFlags(media);
            media.setPrimary(true);
        } else if (Boolean.FALSE.equals(request.getPrimary())) {
            media.setPrimary(false);
        }
        return toResponse(media);
    }

    @Override
    @Transactional
    public void delete(String username, Long mediaId) {
        User user = guard.requireUser(username);
        MediaAsset media = requireManageable(user, mediaId);
        mediaRepository.delete(media);
        // Xoá object SAU khi commit: nếu xoá trước mà giao dịch rollback thì bản
        // ghi còn nhưng file đã mất — ảnh vỡ vĩnh viễn, không khôi phục được.
        deleteObjectsAfterCommit(media);
        log.info("Media {} deleted by {}", mediaId, username);
    }

    @Override
    @Transactional
    public List<MediaResponse> reorder(String username, MediaEntityType entityType, Long entityId,
                                       MediaImageType imageType, List<Long> orderedIds) {
        User user = guard.requireUser(username);
        guard.requireCanManage(user, entityType, entityId);
        List<MediaAsset> assets = mediaRepository
                .findByEntityTypeAndEntityIdAndImageTypeOrderBySortOrderAscIdAsc(entityType, entityId, imageType);
        Map<Long, MediaAsset> byId = assets.stream()
                .collect(Collectors.toMap(MediaAsset::getId, m -> m));
        int order = 0;
        for (Long id : orderedIds) {
            MediaAsset asset = byId.get(id);
            if (asset == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "Media " + id + " does not belong to this gallery");
            }
            asset.setSortOrder(order++);
        }
        return assets.stream()
                .sorted(Comparator.comparingInt(MediaAsset::getSortOrder).thenComparing(MediaAsset::getId))
                .map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public void deleteAllForEntity(MediaEntityType entityType, Long entityId) {
        if (entityId == null) return;
        List<MediaAsset> assets =
                mediaRepository.findByEntityTypeAndEntityIdOrderBySortOrderAscIdAsc(entityType, entityId);
        if (assets.isEmpty()) return;
        mediaRepository.deleteAll(assets);
        assets.forEach(this::deleteObjectsAfterCommit);
    }

    // ------------------------------------------------------------------
    // Gắn ảnh nháp vào entity
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public List<MediaAsset> attach(String username, MediaEntityType entityType, Long entityId,
                                   MediaImageType imageType, List<Long> mediaIds) {
        if (mediaIds == null || mediaIds.isEmpty()) return List.of();
        if (mediaIds.size() > properties.getMaxImagesPerEntity()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "At most " + properties.getMaxImagesPerEntity() + " images can be attached");
        }
        List<MediaAsset> attached = new ArrayList<>();
        int order = 0;
        for (Long mediaId : mediaIds) {
            MediaAsset media = mediaRepository.findByIdAndOwner_Username(mediaId, username)
                    // 404 chứ không 403: người dùng không cần biết ảnh đó có tồn tại
                    // của người khác hay không.
                    .orElseThrow(() -> new ResourceNotFoundException("Media", mediaId));
            if (media.getEntityType() != entityType || media.getImageType() != imageType) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "Media " + mediaId + " was not uploaded for this purpose");
            }
            if (media.getEntityId() != null && !media.getEntityId().equals(entityId)) {
                throw new BusinessException(ErrorCode.INVALID_STATE,
                        "Media " + mediaId + " is already attached to another record");
            }
            if (media.getEntityId() == null) {
                moveToEntityFolder(media, entityType, entityId, imageType);
                media.setEntityId(entityId);
            }
            media.setSortOrder(order++);
            attached.add(media);
        }
        return attached;
    }

    /**
     * Ảnh nháp nằm ở {@code reviews/pending/{userId}/...}; khi review đã có id thì
     * chuyển sang {@code reviews/{id}/images/...} cho đúng cấu trúc bucket. Move
     * thất bại KHÔNG làm hỏng luồng: key cũ vẫn đọc được, chỉ là đường dẫn không đẹp.
     */
    private void moveToEntityFolder(MediaAsset media, MediaEntityType entityType, Long entityId,
                                    MediaImageType imageType) {
        String directory = directoryFor(entityType, entityId, imageType, null);
        String filename = media.getStorageKey().substring(media.getStorageKey().lastIndexOf('/') + 1);
        String targetKey = directory + "/" + filename;
        if (targetKey.equals(media.getStorageKey())) return;
        try {
            storageService.move(media.getStorageKey(), targetKey);
            if (media.getThumbnailKey() != null) {
                String thumbName = media.getThumbnailKey()
                        .substring(media.getThumbnailKey().lastIndexOf('/') + 1);
                String targetThumb = directory + "/thumb/" + thumbName;
                storageService.move(media.getThumbnailKey(), targetThumb);
                media.setThumbnailKey(targetThumb);
            }
            media.setStorageKey(targetKey);
            if (media.getUrl() != null) media.setUrl(storageService.getUrl(targetKey));
        } catch (Exception e) {
            log.warn("Could not move media {} to {}: {}", media.getId(), targetKey, e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    @Override
    public MediaResponse toResponse(MediaAsset media) {
        return MediaResponse.builder()
                .id(media.getId())
                .entityType(media.getEntityType())
                .entityId(media.getEntityId())
                .imageType(media.getImageType())
                .url(urlResolver.urlFor(media))
                .thumbnailUrl(urlResolver.thumbnailUrlFor(media))
                .originalName(media.getOriginalName())
                .mimeType(media.getMimeType())
                .fileSize(media.getFileSize())
                .width(media.getWidth())
                .height(media.getHeight())
                .caption(media.getCaption())
                .sortOrder(media.getSortOrder())
                .primary(media.isPrimary())
                .createdAt(media.getCreatedAt())
                .build();
    }

    private MediaAsset requireManageable(User user, Long mediaId) {
        MediaAsset media = mediaRepository.findById(mediaId)
                .orElseThrow(() -> new ResourceNotFoundException("Media", mediaId));
        // Ảnh nháp: chỉ người upload đụng được. Ảnh đã gắn entity: theo quyền của entity
        // (gym operator xoá được ảnh gym kể cả khi nhân viên khác upload).
        if (media.getEntityId() == null) {
            if (!media.getOwner().getId().equals(user.getId()) && !guard.isPrivileged(user)) {
                throw new ResourceNotFoundException("Media", mediaId);
            }
            return media;
        }
        guard.requireCanManage(user, media.getEntityType(), media.getEntityId());
        return media;
    }

    private boolean hasPrimary(MediaEntityType entityType, Long entityId, MediaImageType imageType) {
        return mediaRepository
                .findByEntityTypeAndEntityIdAndImageTypeOrderBySortOrderAscIdAsc(entityType, entityId, imageType)
                .stream().anyMatch(MediaAsset::isPrimary);
    }

    private void clearPrimaryFlags(MediaAsset media) {
        if (media.getEntityId() == null) return;
        mediaRepository.findByEntityTypeAndEntityIdAndImageTypeOrderBySortOrderAscIdAsc(
                        media.getEntityType(), media.getEntityId(), media.getImageType())
                .forEach(m -> m.setPrimary(false));
    }

    /**
     * Cấu trúc object key. {@code ownerId} chỉ dùng cho ảnh nháp để hai người soạn
     * đánh giá cùng lúc không đụng thư mục nhau.
     */
    private String directoryFor(MediaEntityType entityType, Long entityId,
                                MediaImageType imageType, Long ownerId) {
        if (entityId == null) {
            return entityType.getFolder() + "/pending/" + ownerId;
        }
        return entityType.getFolder() + "/" + entityId + "/" + imageType.getFolder();
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Could not read the uploaded file");
        }
    }

    /** Tên gốc chỉ để hiển thị — cắt path và giới hạn độ dài, không bao giờ vào object key. */
    private String sanitizeName(String originalName) {
        if (originalName == null || originalName.isBlank()) return null;
        String name = originalName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    private void deleteObjectsOnRollback(String storageKey) {
        registerAfterCompletion(status -> {
            if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                log.warn("Transaction rolled back - removing orphaned object {}", storageKey);
                storageService.delete(storageKey);
            }
        }, () -> { /* không có transaction: caller tự chịu, không xoá nhầm */ });
    }

    private void deleteObjectsAfterCommit(MediaAsset media) {
        registerAfterCompletion(status -> {
            if (status == TransactionSynchronization.STATUS_COMMITTED) {
                storageService.delete(media.getStorageKey());
                if (media.getThumbnailKey() != null) storageService.delete(media.getThumbnailKey());
            }
        }, () -> {
            storageService.delete(media.getStorageKey());
            if (media.getThumbnailKey() != null) storageService.delete(media.getThumbnailKey());
        });
    }

    private void registerAfterCompletion(java.util.function.IntConsumer action, Runnable fallback) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            fallback.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                try {
                    action.accept(status);
                } catch (Exception e) {
                    log.warn("Storage cleanup failed: {}", e.getMessage());
                }
            }
        });
    }
}
