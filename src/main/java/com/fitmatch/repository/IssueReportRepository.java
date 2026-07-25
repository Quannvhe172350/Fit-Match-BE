package com.fitmatch.repository;

import com.fitmatch.common.enums.IssueTargetType;
import com.fitmatch.common.enums.ReportStatus;
import com.fitmatch.entity.IssueReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IssueReportRepository extends JpaRepository<IssueReport, Long> {

    /** Chống spam: mỗi người một report đang mở cho mỗi đối tượng. */
    boolean existsByCreatedByAndTargetTypeAndTargetIdAndStatus(
            String createdBy, IssueTargetType targetType, Long targetId, ReportStatus status);

    Page<IssueReport> findByCreatedBy(String createdBy, Pageable pageable);

    Page<IssueReport> findByStatus(ReportStatus status, Pageable pageable);
}
