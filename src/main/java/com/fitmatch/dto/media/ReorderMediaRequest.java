package com.fitmatch.dto.media;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** Sắp xếp lại thư viện ảnh: danh sách id theo đúng thứ tự mong muốn. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReorderMediaRequest {

    @NotEmpty(message = "mediaIds is required")
    private List<Long> mediaIds;
}
