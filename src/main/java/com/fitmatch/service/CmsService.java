package com.fitmatch.service;

import com.fitmatch.common.enums.CmsType;
import com.fitmatch.dto.cms.CmsContentRequest;
import com.fitmatch.dto.cms.CmsContentResponse;

import java.util.List;

/** Nội dung CMS & chiến dịch nổi bật (UC-074). */
public interface CmsService {

    // ----- Public -----
    List<CmsContentResponse> publicByType(CmsType type);

    // ----- Admin -----
    List<CmsContentResponse> adminList(CmsType type);

    CmsContentResponse create(CmsContentRequest request);

    CmsContentResponse update(Long id, CmsContentRequest request);

    void delete(Long id);
}
