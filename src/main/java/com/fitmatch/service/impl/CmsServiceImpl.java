package com.fitmatch.service.impl;

import com.fitmatch.common.enums.CmsType;
import com.fitmatch.dto.cms.CmsContentRequest;
import com.fitmatch.dto.cms.CmsContentResponse;
import com.fitmatch.entity.CmsContent;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.CmsContentRepository;
import com.fitmatch.service.CmsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CmsServiceImpl implements CmsService {

    private final CmsContentRepository repository;

    @Override
    @Transactional(readOnly = true)
    public List<CmsContentResponse> publicByType(CmsType type) {
        return repository.findByTypeAndPublishedTrueOrderBySortOrderAscIdAsc(type).stream()
                .map(CmsContentResponse::of).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CmsContentResponse> adminList(CmsType type) {
        var list = type != null
                ? repository.findByTypeOrderBySortOrderAscIdAsc(type)
                : repository.findAllByOrderByTypeAscSortOrderAsc();
        return list.stream().map(CmsContentResponse::of).toList();
    }

    @Override
    @Transactional
    public CmsContentResponse create(CmsContentRequest request) {
        CmsContent c = CmsContent.builder()
                .type(request.getType())
                .title(request.getTitle())
                .body(request.getBody())
                .imageUrl(request.getImageUrl())
                .link(request.getLink())
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .published(request.getPublished() != null && request.getPublished())
                .build();
        return CmsContentResponse.of(repository.save(c));
    }

    @Override
    @Transactional
    public CmsContentResponse update(Long id, CmsContentRequest request) {
        CmsContent c = require(id);
        c.setType(request.getType());
        c.setTitle(request.getTitle());
        c.setBody(request.getBody());
        c.setImageUrl(request.getImageUrl());
        c.setLink(request.getLink());
        if (request.getSortOrder() != null) c.setSortOrder(request.getSortOrder());
        if (request.getPublished() != null) c.setPublished(request.getPublished());
        return CmsContentResponse.of(c);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        repository.delete(require(id));
    }

    private CmsContent require(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CMS content", id));
    }
}
