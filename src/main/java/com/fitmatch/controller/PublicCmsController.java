package com.fitmatch.controller;

import com.fitmatch.common.enums.CmsType;
import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.dto.cms.CmsContentResponse;
import com.fitmatch.service.CmsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/public/cms")
@RequiredArgsConstructor
@Tag(name = "C. Public - CMS", description = "Nội dung công khai: banner, FAQ, blog, nổi bật (UC-074).")
@SecurityRequirements // public
public class PublicCmsController {

    private final CmsService cmsService;

    @Operation(
            summary = "UC-074 — Nội dung công khai theo loại",
            description = "Actor: **Guest/Customer**. type ∈ {BANNER, FAQ, BLOG, FEATURED}. Chỉ mục đã published.")
    @GetMapping("/{type}")
    public ResponseEntity<ApiResponse<List<CmsContentResponse>>> byType(@PathVariable CmsType type) {
        return ResponseEntity.ok(ApiResponse.success(cmsService.publicByType(type)));
    }
}
