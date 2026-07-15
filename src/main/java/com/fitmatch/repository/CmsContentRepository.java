package com.fitmatch.repository;

import com.fitmatch.common.enums.CmsType;
import com.fitmatch.entity.CmsContent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CmsContentRepository extends JpaRepository<CmsContent, Long> {

    /** UC-074: public — chỉ mục đã published của một loại, sắp theo sortOrder. */
    List<CmsContent> findByTypeAndPublishedTrueOrderBySortOrderAscIdAsc(CmsType type);

    /** Admin — toàn bộ theo loại. */
    List<CmsContent> findByTypeOrderBySortOrderAscIdAsc(CmsType type);

    List<CmsContent> findAllByOrderByTypeAscSortOrderAsc();
}
