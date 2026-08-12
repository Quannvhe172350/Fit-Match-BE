package com.fitmatch.dto.gym;

import com.fitmatch.dto.media.MediaResponse;
import com.fitmatch.entity.GymFacility;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Comparator;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FacilityResponse {

    private Long id;
    private String name;
    private String description;
    private Long branchId;
    private String branchName;
    private boolean active;

    /** Ảnh minh hoạ (media FACILITY/GALLERY) theo thứ tự thư viện. */
    private List<MediaResponse> images;

    /** Ảnh đại diện để render thumbnail trên card — ảnh chính, nếu không có thì ảnh đầu. */
    private String imageUrl;

    public static FacilityResponse of(GymFacility f) {
        return of(f, null);
    }

    public static FacilityResponse of(GymFacility f, List<MediaResponse> images) {
        List<MediaResponse> gallery = images != null ? images : List.of();
        return FacilityResponse.builder()
                .id(f.getId())
                .name(f.getName())
                .description(f.getDescription())
                .branchId(f.getGymBranch() != null ? f.getGymBranch().getId() : null)
                .branchName(f.getGymBranch() != null ? f.getGymBranch().getName() : null)
                .active(f.isActive())
                .images(gallery)
                .imageUrl(coverOf(gallery))
                .build();
    }

    /** Ảnh chính đứng trước; danh sách vào đây đã sắp theo sortOrder nên chỉ cần ưu tiên cờ primary. */
    private static String coverOf(List<MediaResponse> gallery) {
        return gallery.stream()
                .min(Comparator.comparing((MediaResponse m) -> !m.isPrimary()))
                .map(m -> m.getThumbnailUrl() != null ? m.getThumbnailUrl() : m.getUrl())
                .orElse(null);
    }
}
