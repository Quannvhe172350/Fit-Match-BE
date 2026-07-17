package com.fitmatch.dto.gym;

import com.fitmatch.entity.GymDocument;
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
public class GymDocumentDto {

    /**
     * ID tài liệu — cần cho DELETE /api/gym/documents/{id} (UC-012). Trước đây
     * DTO không expose id nên client không có cách hợp lệ nào để xóa từng tài liệu.
     * Chỉ đọc (server set khi trả về); bỏ qua khi tạo mới.
     */
    private Long id;

    @NotBlank(message = "Document type is required")
    @Size(max = 100)
    private String documentType;

    @NotBlank(message = "File URL is required")
    @Size(max = 500)
    private String fileUrl;

    public static GymDocumentDto of(GymDocument d) {
        return GymDocumentDto.builder()
                .id(d.getId())
                .documentType(d.getDocumentType())
                .fileUrl(d.getFileUrl())
                .build();
    }
}
