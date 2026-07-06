package com.fitmatch.dto.pt;

import com.fitmatch.entity.BlockedTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlockedTimeResponse {

    private Long id;
    private Long ptId;
    private Long branchId;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private String reason;

    public static BlockedTimeResponse of(BlockedTime b) {
        return BlockedTimeResponse.builder()
                .id(b.getId())
                .ptId(b.getPtProfile() != null ? b.getPtProfile().getId() : null)
                .branchId(b.getGymBranch() != null ? b.getGymBranch().getId() : null)
                .startAt(b.getStartAt())
                .endAt(b.getEndAt())
                .reason(b.getReason())
                .build();
    }
}
