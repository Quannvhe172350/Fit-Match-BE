package com.fitmatch.dto.cms;

import com.fitmatch.common.enums.CmsType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Admin tạo/cập nhật nội dung CMS (UC-074). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CmsContentRequest {

    @NotNull(message = "type is required")
    private CmsType type;

    @NotBlank(message = "title is required")
    @Size(max = 200)
    private String title;

    private String body;

    @Size(max = 500)
    private String imageUrl;

    @Size(max = 500)
    private String link;

    private Integer sortOrder;
    private Boolean published;
}
