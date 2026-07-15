package com.fitmatch.repository;

import com.fitmatch.common.enums.ReportStatus;
import com.fitmatch.entity.ReviewReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReviewReportRepository extends JpaRepository<ReviewReport, Long> {

    Page<ReviewReport> findByStatusOrderByIdDesc(ReportStatus status, Pageable pageable);

    boolean existsByReview_IdAndCreatedByAndStatus(Long reviewId, String createdBy, ReportStatus status);
}
