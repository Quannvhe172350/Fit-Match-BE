package com.fitmatch.dto.pt;

import com.fitmatch.entity.PtAssignment;
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
public class PtAssignmentResponse {

    private Long id;
    private Long ptId;
    private Long branchId;
    private String branchName;
    private boolean active;

    public static PtAssignmentResponse of(PtAssignment a) {
        return PtAssignmentResponse.builder()
                .id(a.getId())
                .ptId(a.getPtProfile().getId())
                .branchId(a.getGymBranch() != null ? a.getGymBranch().getId() : null)
                .branchName(a.getGymBranch() != null ? a.getGymBranch().getName() : null)
                .active(a.isActive())
                .build();
    }
}
