package com.fitmatch.service;

import com.fitmatch.common.enums.MediaImageType;
import com.fitmatch.dto.gym.GymMediaResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Ảnh công khai của Gym và chi nhánh (UC-016). Từ V64 đây chỉ là lớp mỏng bọc
 * {@link MediaService} — giữ route {@code /api/gym/media} quen thuộc cho FE nhưng
 * file và metadata đi qua đúng một đường như mọi loại ảnh khác.
 */
public interface GymMediaService {

    /** Upload ảnh cho Gym (branchId null) hoặc cho một chi nhánh cụ thể. */
    List<GymMediaResponse> upload(String username, Long branchId, MediaImageType imageType,
                                  String caption, MultipartFile[] files);

    void delete(String username, Long mediaId);

    /** branchId null = toàn bộ ảnh của Gym (kể cả ảnh riêng của các chi nhánh). */
    List<GymMediaResponse> list(String username, Long branchId);
}
