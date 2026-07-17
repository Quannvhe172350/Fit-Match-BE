package com.fitmatch.dto;

import com.fitmatch.dto.gym.GymDocumentDto;
import com.fitmatch.entity.GymDocument;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** UC-012: DTO phải expose id để client gọi DELETE /api/gym/documents/{id}. */
class GymDocumentDtoTest {

    @Test
    void of_exposesId() {
        GymDocument d = GymDocument.builder()
                .id(42L).documentType("BUSINESS_LICENSE").fileUrl("/api/files/documents/x.pdf")
                .build();

        GymDocumentDto dto = GymDocumentDto.of(d);

        assertThat(dto.getId()).isEqualTo(42L);
        assertThat(dto.getDocumentType()).isEqualTo("BUSINESS_LICENSE");
        assertThat(dto.getFileUrl()).isEqualTo("/api/files/documents/x.pdf");
    }
}
