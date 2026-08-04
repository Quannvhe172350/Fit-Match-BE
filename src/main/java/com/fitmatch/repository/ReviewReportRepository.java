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

    /**
     * Bug S2-07: dùng khi KHÔNG lọc theo trạng thái. Giữ nguyên thứ tự id giảm dần
     * như bản có lọc để hai chế độ hiển thị cùng một trật tự.
     */
    Page<ReviewReport> findAllByOrderByIdDesc(Pageable pageable);

    boolean existsByReview_IdAndCreatedByAndStatus(Long reviewId, String createdBy, ReportStatus status);

    /** P0-0.4: có bất kỳ report nào trỏ tới review — dùng để tránh hard-delete vi phạm FK. */
    boolean existsByReview_Id(Long reviewId);
}
