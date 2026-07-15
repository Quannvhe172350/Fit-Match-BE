package com.fitmatch.dto.cms;

import com.fitmatch.common.enums.CmsType;
import com.fitmatch.entity.CmsContent;
import lombok.Builder;
import lombok.Getter;

/** Nội dung CMS (UC-074). */
@Getter
@Builder
public class CmsContentResponse {

    private Long id;
    private CmsType type;
    private String title;
    private String body;
    private String imageUrl;
    private String link;
    private int sortOrder;
    private boolean published;

    public static CmsContentResponse of(CmsContent c) {
        return CmsContentResponse.builder()
                .id(c.getId())
                .type(c.getType())
                .title(c.getTitle())
                .body(c.getBody())
                .imageUrl(c.getImageUrl())
                .link(c.getLink())
                .sortOrder(c.getSortOrder())
                .published(c.isPublished())
                .build();
    }
}
