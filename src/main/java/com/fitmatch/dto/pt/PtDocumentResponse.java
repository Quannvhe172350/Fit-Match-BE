package com.fitmatch.dto.pt;

import com.fitmatch.entity.PtDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Tài liệu hồ sơ PT kèm id để Gym quản lý (UC-020).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PtDocumentResponse {

    private Long id;
    private String documentType;
    private String fileUrl;

    public static PtDocumentResponse of(PtDocument d) {
        return PtDocumentResponse.builder()
                .id(d.getId())
                .documentType(d.getDocumentType())
                .fileUrl(d.getFileUrl())
                .build();
    }
}
