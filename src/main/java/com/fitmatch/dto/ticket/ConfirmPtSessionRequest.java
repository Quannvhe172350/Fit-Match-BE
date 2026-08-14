package com.fitmatch.dto.ticket;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Câu 31/33: gym xác nhận PT có đến buổi tập, kèm ảnh bằng chứng. Không chặn
 * dòng tiền — chỉ để lại dấu vết khi có tranh chấp về sau.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmPtSessionRequest {

    @NotBlank(message = "evidenceUrl is required")
    @Size(max = 500)
    private String evidenceUrl;
}
