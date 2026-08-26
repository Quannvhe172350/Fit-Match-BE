package com.fitmatch.service;

import com.fitmatch.common.enums.ReportStatus;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.review.ModerateReviewRequest;
import com.fitmatch.dto.review.RatingSummaryResponse;
import com.fitmatch.dto.review.ReportRequest;
import com.fitmatch.dto.review.ReviewReportResponse;
import com.fitmatch.dto.review.TicketReviewRequest;
import com.fitmatch.dto.review.ReviewResponse;
import org.springframework.data.domain.Pageable;

/**
 * Đánh giá & kiểm duyệt (UC-069..071). Review chỉ tạo cho booking COMPLETED của
 * chính khách; ngữ cảnh Gym/dịch vụ/PT lấy từ booking — khách chỉ đánh giá được
 * gym mình đã mua dịch vụ và chỉ chấm điểm PT khi buổi đó có PT. Review một
 * chiều: chỉ hiển thị, không có phản hồi của gym (V59). Điểm công khai chỉ tính
 * review VISIBLE.
 */
public interface ReviewService {

    // ----- Customer (UC-069/070) -----
    // Tạo đánh giá: POST /api/tickets/{id}/review (gym) và
    // POST /api/sessions/{id}/review (PT) — xem TicketReviewService.

    ReviewResponse update(String customerUsername, Long reviewId, TicketReviewRequest request);

    void delete(String customerUsername, Long reviewId);

    PageResponse<ReviewResponse> myReviews(String customerUsername, Pageable pageable);

    /** UC-070: bất kỳ actor đã đăng nhập báo cáo một review công khai. */
    void report(String reporterUsername, Long reviewId, ReportRequest request);

    // ----- Public (UC-009) -----
    PageResponse<ReviewResponse> visibleForGym(Long gymProfileId, Pageable pageable);

    PageResponse<ReviewResponse> visibleForPt(Long ptProfileId, Pageable pageable);

    /** UC-009: điểm trung bình + phổ điểm 1..5 sao của một gym (chỉ review VISIBLE). */
    RatingSummaryResponse gymRatingSummary(Long gymProfileId);

    RatingSummaryResponse ptRatingSummary(Long ptProfileId);

    // ----- Gym (UC-023) -----
    /**
     * Đánh giá của gym mình. {@code targetType} null = lấy tất cả (giữ nguyên
     * hành vi cũ); GYM = chỉ đánh giá phòng gym; PT = chỉ đánh giá huấn luyện
     * viên. Hai loại này chấm hai thứ khác hẳn nhau nên trộn chung một danh sách
     * thì gym không đọc được gì.
     */
    PageResponse<ReviewResponse> gymReviews(String gymUsername,
                                            com.fitmatch.common.enums.ReviewTargetType targetType,
                                            Pageable pageable);

    // ----- Moderator/Admin (UC-071) -----
    PageResponse<ReviewReportResponse> reports(ReportStatus status, Pageable pageable);

    ReviewResponse moderate(String moderatorUsername, Long reviewId, ModerateReviewRequest request);

    ReviewReportResponse resolveReport(String moderatorUsername, Long reportId, boolean dismiss, String note);
}
