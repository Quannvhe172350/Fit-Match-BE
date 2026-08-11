package com.fitmatch.service;

import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.MediaEntityType;
import com.fitmatch.common.enums.MediaImageType;
import com.fitmatch.common.enums.Role;
import com.fitmatch.config.MediaProperties;
import com.fitmatch.dto.media.MediaResponse;
import com.fitmatch.entity.MediaAsset;
import com.fitmatch.entity.User;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.MediaAssetRepository;
import com.fitmatch.service.impl.MediaServiceImpl;
import com.fitmatch.service.support.ImageFileValidator;
import com.fitmatch.service.support.ImageProcessor;
import com.fitmatch.service.support.MediaAccessGuard;
import com.fitmatch.service.support.MediaUrlResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MediaServiceImplTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

    @Mock private MediaAssetRepository mediaRepository;
    @Mock private StorageService storageService;
    @Mock private ImageProcessor imageProcessor;
    @Mock private MediaAccessGuard guard;
    @Mock private MediaUrlResolver urlResolver;

    private MediaProperties properties;
    private MediaServiceImpl service;

    private final User owner = User.builder().id(1L).username("john").role(Role.ROLE_CUSTOMER).build();

    @BeforeEach
    void setUp() {
        properties = new MediaProperties();
        service = new MediaServiceImpl(mediaRepository, storageService, properties,
                new ImageFileValidator(properties), imageProcessor, guard, urlResolver);

        when(guard.requireUser("john")).thenReturn(owner);
        when(imageProcessor.readDimensions(any())).thenReturn(new ImageProcessor.Dimensions(800, 600));
        when(imageProcessor.createThumbnail(any())).thenReturn(new ImageProcessor.Thumbnail(null, null));
        when(storageService.put(anyString(), any(), anyString()))
                .thenAnswer(inv -> new StorageService.StoredObject(
                        inv.getArgument(0), "fit-match-test",
                        "https://storage.googleapis.com/fit-match-test/" + inv.getArgument(0)));
        when(mediaRepository.save(any(MediaAsset.class))).thenAnswer(inv -> {
            MediaAsset asset = inv.getArgument(0);
            if (asset.getId() == null) asset.setId(100L);
            return asset;
        });
    }

    private MultipartFile jpeg(String name) {
        return new MockMultipartFile("files", name, "image/jpeg", JPEG);
    }

    @Test
    void upload_generatesUuidKeyAndNeverUsesClientFilename() {
        service.upload("john", MediaEntityType.REVIEW, 42L, MediaImageType.REVIEW_IMAGE,
                new MultipartFile[]{jpeg("../../etc/passwd.jpg")});

        var keyCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(storageService).put(keyCaptor.capture(), any(), eq("image/jpeg"));
        String key = keyCaptor.getValue();

        assertThat(key).startsWith("reviews/42/images/").endsWith(".jpg");
        // Tên client gửi lên không được xuất hiện trong object key ở bất kỳ dạng nào.
        assertThat(key).doesNotContain("..").doesNotContain("passwd");
    }

    @Test
    void upload_draftGoesToPendingFolderScopedToOwner() {
        service.upload("john", MediaEntityType.REVIEW, null, MediaImageType.REVIEW_IMAGE,
                new MultipartFile[]{jpeg("a.jpg")});

        var keyCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(storageService).put(keyCaptor.capture(), any(), anyString());
        assertThat(keyCaptor.getValue()).startsWith("reviews/pending/1/");
    }

    @Test
    void upload_singletonType_replacesAndDeletesThePreviousImage() {
        MediaAsset old = MediaAsset.builder().id(9L).storageKey("users/1/avatar/old.jpg")
                .entityType(MediaEntityType.USER).entityId(1L)
                .imageType(MediaImageType.AVATAR).owner(owner).build();
        when(mediaRepository.findByEntityTypeAndEntityIdAndImageTypeOrderBySortOrderAscIdAsc(
                MediaEntityType.USER, 1L, MediaImageType.AVATAR))
                .thenReturn(List.of(old));

        service.upload("john", MediaEntityType.USER, 1L, MediaImageType.AVATAR,
                new MultipartFile[]{jpeg("new.jpg")});

        verify(mediaRepository).deleteAll(List.of(old));
        // Không có transaction đang chạy trong unit test -> nhánh dự phòng xoá ngay.
        verify(storageService).delete("users/1/avatar/old.jpg");
    }

    @Test
    void upload_beyondGalleryLimit_isRejectedBeforeTouchingStorage() {
        properties.setMaxImagesPerEntity(3);
        when(mediaRepository.countByEntityTypeAndEntityIdAndImageType(
                MediaEntityType.GYM, 5L, MediaImageType.GALLERY)).thenReturn(3L);

        assertThatThrownBy(() -> service.upload("john", MediaEntityType.GYM, 5L,
                MediaImageType.GALLERY, new MultipartFile[]{jpeg("a.jpg")}))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);

        verify(storageService, never()).put(anyString(), any(), anyString());
    }

    @Test
    void upload_withoutPermissionOnEntity_neverReachesStorage() {
        doThrow(new BusinessException(ErrorCode.FORBIDDEN, "nope"))
                .when(guard).requireCanManage(owner, MediaEntityType.GYM, 999L);

        assertThatThrownBy(() -> service.upload("john", MediaEntityType.GYM, 999L,
                MediaImageType.GALLERY, new MultipartFile[]{jpeg("a.jpg")}))
                .isInstanceOf(BusinessException.class);

        verify(storageService, never()).put(anyString(), any(), anyString());
        verify(mediaRepository, never()).save(any());
    }

    @Test
    void upload_firstImageOfAnEmptyGallery_becomesPrimary() {
        var responses = service.upload("john", MediaEntityType.GYM, 5L, MediaImageType.GALLERY,
                new MultipartFile[]{jpeg("a.jpg")});

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).isPrimary()).isTrue();
    }

    @Test
    void attach_mediaOwnedBySomeoneElse_isRejected() {
        // Chốt chặn quan trọng nhất của luồng review: không mượn được ảnh người khác.
        when(mediaRepository.findByIdAndOwner_Username(55L, "john")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.attach("john", MediaEntityType.REVIEW, 7L,
                MediaImageType.REVIEW_IMAGE, List.of(55L)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void attach_mediaAlreadyBoundToAnotherRecord_isRejected() {
        MediaAsset other = MediaAsset.builder().id(55L).entityType(MediaEntityType.REVIEW)
                .entityId(999L).imageType(MediaImageType.REVIEW_IMAGE)
                .storageKey("reviews/999/images/x.jpg").owner(owner).build();
        when(mediaRepository.findByIdAndOwner_Username(55L, "john")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.attach("john", MediaEntityType.REVIEW, 7L,
                MediaImageType.REVIEW_IMAGE, List.of(55L)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_STATE);
    }

    @Test
    void attach_mediaUploadedForAnotherPurpose_isRejected() {
        MediaAsset avatar = MediaAsset.builder().id(55L).entityType(MediaEntityType.USER)
                .imageType(MediaImageType.AVATAR).storageKey("users/pending/1/x.jpg")
                .owner(owner).build();
        when(mediaRepository.findByIdAndOwner_Username(55L, "john")).thenReturn(Optional.of(avatar));

        assertThatThrownBy(() -> service.attach("john", MediaEntityType.REVIEW, 7L,
                MediaImageType.REVIEW_IMAGE, List.of(55L)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void attach_draftMedia_movesObjectIntoTheEntityFolder() {
        MediaAsset draft = MediaAsset.builder().id(55L).entityType(MediaEntityType.REVIEW)
                .imageType(MediaImageType.REVIEW_IMAGE)
                .storageKey("reviews/pending/1/abc.jpg")
                .url("https://storage.googleapis.com/fit-match-test/reviews/pending/1/abc.jpg")
                .owner(owner).build();
        when(mediaRepository.findByIdAndOwner_Username(55L, "john")).thenReturn(Optional.of(draft));

        service.attach("john", MediaEntityType.REVIEW, 7L, MediaImageType.REVIEW_IMAGE, List.of(55L));

        verify(storageService).move("reviews/pending/1/abc.jpg", "reviews/7/images/abc.jpg");
        assertThat(draft.getStorageKey()).isEqualTo("reviews/7/images/abc.jpg");
        assertThat(draft.getEntityId()).isEqualTo(7L);
    }

    @Test
    void attach_moveFailure_keepsTheImageUsable() {
        // Đường dẫn đẹp là chuyện phụ; mất ảnh của khách mới là hỏng.
        MediaAsset draft = MediaAsset.builder().id(55L).entityType(MediaEntityType.REVIEW)
                .imageType(MediaImageType.REVIEW_IMAGE)
                .storageKey("reviews/pending/1/abc.jpg").owner(owner).build();
        when(mediaRepository.findByIdAndOwner_Username(55L, "john")).thenReturn(Optional.of(draft));
        doThrow(new RuntimeException("copy failed")).when(storageService).move(anyString(), anyString());

        service.attach("john", MediaEntityType.REVIEW, 7L, MediaImageType.REVIEW_IMAGE, List.of(55L));

        assertThat(draft.getEntityId()).isEqualTo(7L);
        assertThat(draft.getStorageKey()).isEqualTo("reviews/pending/1/abc.jpg");
    }

    @Test
    void delete_removesRecordAndObjectIncludingThumbnail() {
        MediaAsset media = MediaAsset.builder().id(60L).entityType(MediaEntityType.GYM).entityId(5L)
                .imageType(MediaImageType.GALLERY).storageKey("gyms/5/gallery/a.jpg")
                .thumbnailKey("gyms/5/gallery/thumb/a.jpg").owner(owner).build();
        when(mediaRepository.findById(60L)).thenReturn(Optional.of(media));

        service.delete("john", 60L);

        verify(mediaRepository).delete(media);
        verify(storageService).delete("gyms/5/gallery/a.jpg");
        verify(storageService).delete("gyms/5/gallery/thumb/a.jpg");
    }

    @Test
    void delete_draftOfAnotherUser_reportsNotFound() {
        MediaAsset draft = MediaAsset.builder().id(60L).entityType(MediaEntityType.REVIEW)
                .imageType(MediaImageType.REVIEW_IMAGE).storageKey("reviews/pending/2/a.jpg")
                .owner(User.builder().id(2L).username("mallory").build()).build();
        when(mediaRepository.findById(60L)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.delete("john", 60L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(mediaRepository, never()).delete(any());
    }

    @Test
    void reorder_mediaFromAnotherGallery_isRejected() {
        when(mediaRepository.findByEntityTypeAndEntityIdAndImageTypeOrderBySortOrderAscIdAsc(
                MediaEntityType.GYM, 5L, MediaImageType.GALLERY))
                .thenReturn(List.of(MediaAsset.builder().id(1L).owner(owner).build()));

        assertThatThrownBy(() -> service.reorder("john", MediaEntityType.GYM, 5L,
                MediaImageType.GALLERY, List.of(1L, 2L)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void reorder_assignsSortOrderInTheGivenSequence() {
        MediaAsset first = MediaAsset.builder().id(1L).sortOrder(0).owner(owner).build();
        MediaAsset second = MediaAsset.builder().id(2L).sortOrder(1).owner(owner).build();
        when(mediaRepository.findByEntityTypeAndEntityIdAndImageTypeOrderBySortOrderAscIdAsc(
                MediaEntityType.GYM, 5L, MediaImageType.GALLERY))
                .thenReturn(List.of(first, second));

        service.reorder("john", MediaEntityType.GYM, 5L, MediaImageType.GALLERY, List.of(2L, 1L));

        assertThat(second.getSortOrder()).isZero();
        assertThat(first.getSortOrder()).isEqualTo(1);
    }

    @Test
    void listForEntities_groupsByEntityIdForBatchRendering() {
        when(mediaRepository.findByEntityTypeAndEntityIdInOrderBySortOrderAscIdAsc(
                eq(MediaEntityType.BRANCH), any()))
                .thenReturn(List.of(
                        MediaAsset.builder().id(1L).entityId(10L).entityType(MediaEntityType.BRANCH)
                                .imageType(MediaImageType.GALLERY).owner(owner).build(),
                        MediaAsset.builder().id(2L).entityId(10L).entityType(MediaEntityType.BRANCH)
                                .imageType(MediaImageType.GALLERY).owner(owner).build(),
                        MediaAsset.builder().id(3L).entityId(11L).entityType(MediaEntityType.BRANCH)
                                .imageType(MediaImageType.COVER).owner(owner).build()));

        var grouped = service.listForEntities(MediaEntityType.BRANCH, List.of(10L, 11L),
                MediaImageType.GALLERY);

        assertThat(grouped).containsOnlyKeys(10L);
        assertThat(grouped.get(10L)).extracting(MediaResponse::getId).containsExactly(1L, 2L);
    }
}
