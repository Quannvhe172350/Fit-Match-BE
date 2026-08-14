package com.fitmatch.service.support;

import com.fitmatch.common.enums.MediaEntityType;
import com.fitmatch.common.enums.MediaImageType;
import com.fitmatch.dto.media.MediaResponse;
import com.fitmatch.service.MediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ảnh đại diện hồ sơ PT — media {@code TRAINER/AVATAR} của V64.
 *
 * <p>Gom vào một chỗ vì có tới bốn đường đọc cần nó (hồ sơ của chính PT, hồ sơ
 * công khai, card marketplace, bảng PT của gym) và cả bốn phải nhất quán: cùng
 * một entityType/imageType, cùng quy tắc "chưa có ảnh thì trả null để FE hiện
 * chữ cái đầu". Lặp lại bộ ba enum ở từng service là cách chắc chắn nhất để một
 * màn hình đọc nhầm loại ảnh và hiện ảnh trắng.
 *
 * <p>Upload/xoá đi thẳng qua {@code POST /api/media/upload} — quyền đã có sẵn ở
 * {@code MediaAccessGuard} (PT tự sửa ảnh mình, gym chủ quản cũng sửa được).
 */
@Component
@RequiredArgsConstructor
public class PtAvatarResolver {

    private final MediaService mediaService;

    /** Ảnh của một PT, null nếu chưa đặt. */
    public String urlOf(Long ptProfileId) {
        MediaResponse media = mediaService.primaryFor(
                MediaEntityType.TRAINER, ptProfileId, MediaImageType.AVATAR);
        return media != null ? media.getUrl() : null;
    }

    /**
     * Ảnh của nhiều PT trong MỘT truy vấn — dùng cho mọi danh sách. Gọi
     * {@link #urlOf} trong vòng lặp là N+1 ngay trên trang tìm PT.
     */
    public Map<Long, String> urlsOf(Collection<Long> ptProfileIds) {
        Map<Long, String> urls = new LinkedHashMap<>();
        mediaService.listForEntities(MediaEntityType.TRAINER, ptProfileIds, MediaImageType.AVATAR)
                .forEach((ptId, items) -> {
                    if (!items.isEmpty()) urls.put(ptId, items.get(0).getUrl());
                });
        return urls;
    }
}
