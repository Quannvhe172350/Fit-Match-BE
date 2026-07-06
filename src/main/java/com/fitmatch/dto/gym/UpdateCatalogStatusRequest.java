package com.fitmatch.dto.gym;

import com.fitmatch.common.enums.CatalogStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Đổi trạng thái vòng đời của dịch vụ/gói tập trên marketplace (UC-027).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCatalogStatusRequest {

    @NotNull(message = "status is required")
    private CatalogStatus status;
}
