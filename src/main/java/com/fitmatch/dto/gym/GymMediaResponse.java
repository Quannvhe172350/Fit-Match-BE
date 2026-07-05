package com.fitmatch.dto.gym;

import com.fitmatch.entity.GymMedia;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GymMediaResponse {

    private Long id;
    private String url;
    private String caption;
    private Long branchId;

    public static GymMediaResponse of(GymMedia m) {
        return GymMediaResponse.builder()
                .id(m.getId())
                .url(m.getUrl())
                .caption(m.getCaption())
                .branchId(m.getGymBranch() != null ? m.getGymBranch().getId() : null)
                .build();
    }
}
