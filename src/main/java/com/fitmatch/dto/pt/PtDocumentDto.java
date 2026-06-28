package com.fitmatch.dto.pt;

import com.fitmatch.entity.PtDocument;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
public class PtDocumentDto {

    @NotBlank(message = "Document type is required")
    @Size(max = 100)
    private String documentType;

    @NotBlank(message = "File URL is required")
    @Size(max = 500)
    private String fileUrl;

    public static PtDocumentDto of(PtDocument d) {
        return PtDocumentDto.builder()
                .documentType(d.getDocumentType())
                .fileUrl(d.getFileUrl())
                .build();
    }
}
