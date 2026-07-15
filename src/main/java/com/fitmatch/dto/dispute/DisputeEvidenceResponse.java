package com.fitmatch.dto.dispute;

import com.fitmatch.entity.DisputeEvidence;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/** Bằng chứng tranh chấp (UC-064). */
@Getter
@Builder
public class DisputeEvidenceResponse {

    private Long id;
    private String description;
    private String fileUrl;
    private String submittedBy;
    private LocalDateTime createdAt;

    public static DisputeEvidenceResponse of(DisputeEvidence e) {
        return DisputeEvidenceResponse.builder()
                .id(e.getId())
                .description(e.getDescription())
                .fileUrl(e.getFileUrl())
                .submittedBy(e.getCreatedBy())
                .createdAt(e.getCreatedAt())
                .build();
    }
}
