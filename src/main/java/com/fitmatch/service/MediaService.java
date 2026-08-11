package com.fitmatch.service;

import com.fitmatch.common.enums.MediaEntityType;
import com.fitmatch.common.enums.MediaImageType;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.media.MediaResponse;
import com.fitmatch.dto.media.MediaUpdateRequest;
import com.fitmatch.entity.MediaAsset;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Hệ thống ảnh dùng chung (V64) — mọi màn hình upload ảnh đều đi qua đây, không
 * service nghiệp vụ nào được gọi thẳng SDK storage.
 *
 * <p>Luồng chuẩn: xác thực → phân quyền theo entity → kiểm tra file → sinh object
 * key có uuid → đẩy lên GCS → lưu metadata. Object đã lên storage mà giao dịch DB
 * rollback thì được xoá lại, nên không sinh file mồ côi.
 */
public interface MediaService {

    /**
     * Upload một hoặc nhiều ảnh cho entity. {@code entityId} null = ảnh nháp: dùng
     * khi bản ghi chưa tồn tại (soạn đánh giá), sau đó gắn bằng
     * {@link #attach(String, MediaEntityType, Long, MediaImageType, List)}.
     */
    List<MediaResponse> upload(String username, MediaEntityType entityType, Long entityId,
                               MediaImageType imageType, MultipartFile[] files);

    List<MediaResponse> list(String username, MediaEntityType entityType, Long entityId,
                             MediaImageType imageType);

    PageResponse<MediaResponse> listPaged(String username, MediaEntityType entityType, Long entityId,
                                          MediaImageType imageType, Pageable pageable);

    MediaResponse update(String username, Long mediaId, MediaUpdateRequest request);

    void delete(String username, Long mediaId);

    /** Sắp xếp lại thư viện; chỉ nhận các ảnh thuộc đúng entity/imageType đó. */
    List<MediaResponse> reorder(String username, MediaEntityType entityType, Long entityId,
                                MediaImageType imageType, List<Long> orderedIds);

    /**
     * Gắn ảnh nháp vào entity vừa tạo. Chỉ gắn được ảnh do CHÍNH người dùng upload
     * và chưa thuộc entity nào khác — đây là chốt chặn ngăn việc mượn ảnh của người
     * khác cho review của mình.
     */
    List<MediaAsset> attach(String username, MediaEntityType entityType, Long entityId,
                            MediaImageType imageType, List<Long> mediaIds);

    /** Xoá toàn bộ ảnh của một entity (gọi khi entity bị xoá) — kể cả object trên storage. */
    void deleteAllForEntity(MediaEntityType entityType, Long entityId);

    /** Ảnh của nhiều entity cùng lúc, gom theo entityId — tránh N+1 khi render danh sách. */
    Map<Long, List<MediaResponse>> listForEntities(MediaEntityType entityType,
                                                   Collection<Long> entityIds,
                                                   MediaImageType imageType);

    /** Ảnh chính (isPrimary, hoặc ảnh đầu tiên) của entity — dùng làm thumbnail danh sách. */
    MediaResponse primaryFor(MediaEntityType entityType, Long entityId, MediaImageType imageType);

    MediaResponse toResponse(MediaAsset media);
}
