package com.fitmatch.service.impl;

import com.fitmatch.common.AuditActions;
import com.fitmatch.common.enums.BookingStatus;
import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.ReportStatus;
import com.fitmatch.common.enums.ReviewStatus;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.review.ModerateReviewRequest;
import com.fitmatch.dto.review.ReplyRequest;
import com.fitmatch.dto.review.ReportRequest;
import com.fitmatch.dto.review.ReviewReportResponse;
import com.fitmatch.dto.review.ReviewRequest;
import com.fitmatch.dto.review.ReviewResponse;
import com.fitmatch.entity.Booking;
import com.fitmatch.entity.Review;
import com.fitmatch.entity.ReviewReport;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.BookingRepository;
import com.fitmatch.repository.ReviewReportRepository;
import com.fitmatch.repository.ReviewRepository;
import com.fitmatch.service.AuditService;
import com.fitmatch.service.ReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final ReviewReportRepository reviewReportRepository;
    private final BookingRepository bookingRepository;
    private final AuditService auditService;
    private final com.fitmatch.service.support.NotificationDispatcher notificationDispatcher;
    // UC-008 (V51): đồng bộ cột denorm avg_rating/rating_count sau mỗi thay đổi review
    private final com.fitmatch.service.support.RatingAggregator ratingAggregator;

    private void refreshDenormRating(Review review) {
        ratingAggregator.refreshGym(review.getGymProfile() != null ? review.getGymProfile().getId() : null);
        ratingAggregator.refreshPt(review.getPtProfile() != null ? review.getPtProfile().getId() : null);
    }

    @Override
    @Transactional
    public ReviewResponse create(String customerUsername, ReviewRequest request) {
        Booking booking = bookingRepository
                .findByIdAndCustomer_Username(request.getBookingId(), customerUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", request.getBookingId()));
        if (booking.getStatus() != BookingStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Only a COMPLETED booking can be reviewed (current: " + booking.getStatus() + ")");
        }
        if (reviewRepository.existsByBooking_Id(booking.getId())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "This booking has already been reviewed");
        }
        Review review = reviewRepository.save(Review.builder()
                .booking(booking)
                .customer(booking.getCustomer())
                .gymProfile(booking.getGymProfile())
                .gymService(booking.getGymService())
                .trainingPackage(booking.getTrainingPackage())
                .ptProfile(booking.getPtProfile())
                .rating(request.getRating())
                .comment(request.getComment())
                .status(ReviewStatus.VISIBLE)
                .build());
        refreshDenormRating(review);
        log.info("Review {} created for booking {} (rating {})",
                review.getId(), booking.getId(), request.getRating());
        return ReviewResponse.of(review);
    }

    @Override
    @Transactional
    public ReviewResponse update(String customerUsername, Long reviewId, ReviewRequest request) {
        Review review = reviewRepository.findByIdAndCustomer_Username(reviewId, customerUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Review", reviewId));
        if (review.getStatus() == ReviewStatus.REMOVED) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "A removed review cannot be edited");
        }
        review.setRating(request.getRating());
        review.setComment(request.getComment());
        refreshDenormRating(review);
        return ReviewResponse.of(review);
    }

    @Override
    @Transactional
    public void delete(String customerUsername, Long reviewId) {
        Review review = reviewRepository.findByIdAndCustomer_Username(reviewId, customerUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Review", reviewId));
        // P0-0.4: review đang bị report có FK từ review_reports (không ON DELETE CASCADE) —
        // hard-delete sẽ vi phạm ràng buộc (409). Chuyển sang soft-delete: đánh dấu REMOVED
        // (ẩn khỏi marketplace, không tính điểm) và giữ nguyên lịch sử report cho kiểm duyệt.
        if (reviewReportRepository.existsByReview_Id(reviewId)) {
            review.setStatus(ReviewStatus.REMOVED);
            refreshDenormRating(review);
            log.info("Review {} soft-removed (has reports) by owner {}", reviewId, customerUsername);
            return;
        }
        reviewRepository.delete(review);
        refreshDenormRating(review);
        log.info("Review {} deleted by owner {}", reviewId, customerUsername);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> myReviews(String customerUsername, Pageable pageable) {
        return PageResponse.of(
                reviewRepository.findByCustomer_UsernameOrderByIdDesc(customerUsername, pageable),
                ReviewResponse::of);
    }

    @Override
    @Transactional
    public void report(String reporterUsername, Long reviewId, ReportRequest request) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review", reviewId));
        // Chống spam: một người chỉ mở một report đang xử lý cho mỗi review.
        if (reviewReportRepository.existsByReview_IdAndCreatedByAndStatus(
                reviewId, reporterUsername, ReportStatus.OPEN)) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "You already have an open report for this review");
        }
        reviewReportRepository.save(ReviewReport.builder()
                .review(review)
                .reason(request.getReason())
                .status(ReportStatus.OPEN)
                .build());
        log.info("Review {} reported by {}", reviewId, reporterUsername);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> visibleForGym(Long gymProfileId, Pageable pageable) {
        return PageResponse.of(
                reviewRepository.findByGymProfile_IdAndStatusOrderByIdDesc(
                        gymProfileId, ReviewStatus.VISIBLE, pageable),
                ReviewResponse::of);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> visibleForPt(Long ptProfileId, Pageable pageable) {
        return PageResponse.of(
                reviewRepository.findByPtProfile_IdAndStatusOrderByIdDesc(
                        ptProfileId, ReviewStatus.VISIBLE, pageable),
                ReviewResponse::of);
    }

    @Override
    @Transactional
    public ReviewResponse reply(String gymUsername, Long reviewId, ReplyRequest request) {
        Review review = reviewRepository.findByIdAndGymProfile_User_Username(reviewId, gymUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Review", reviewId));
        review.setReply(request.getReply());
        review.setRepliedBy(gymUsername);
        review.setRepliedAt(LocalDateTime.now());
        notificationDispatcher.reviewReplied(review);
        return ReviewResponse.of(review);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> gymReviews(String gymUsername, Pageable pageable) {
        return PageResponse.of(
                reviewRepository.findByGymProfile_User_UsernameOrderByIdDesc(gymUsername, pageable),
                ReviewResponse::of);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReviewReportResponse> reports(ReportStatus status, Pageable pageable) {
        // Bug S2-07 (cùng lỗi đã sửa ở RefundServiceImpl): không truyền status
        // = KHÔNG lọc, thay vì ép về OPEN — nếu ép, moderator không có cách nào
        // xem lại các report đã xử lý.
        if (status == null) {
            return PageResponse.of(
                    reviewReportRepository.findAllByOrderByIdDesc(pageable),
                    ReviewReportResponse::of);
        }
        return PageResponse.of(
                reviewReportRepository.findByStatusOrderByIdDesc(status, pageable),
                ReviewReportResponse::of);
    }

    @Override
    @Transactional
    public ReviewResponse moderate(String moderatorUsername, Long reviewId, ModerateReviewRequest request) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review", reviewId));
        // P2-B9: REMOVED là trạng thái cuối (khách tự gỡ hoặc bị gỡ vì vi phạm) — không cho
        // kiểm duyệt đưa ngược lại VISIBLE/HIDDEN.
        if (review.getStatus() == com.fitmatch.common.enums.ReviewStatus.REMOVED) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "A removed review cannot be moderated");
        }
        review.setStatus(request.getStatus());
        refreshDenormRating(review);
        if (request.getStatus() != com.fitmatch.common.enums.ReviewStatus.VISIBLE) {
            notificationDispatcher.reviewModerated(review);
        }
        auditService.record(AuditActions.REVIEW_MODERATE, "Review", reviewId,
                "Set to " + request.getStatus() + " by " + moderatorUsername
                        + (request.getNote() != null ? ": " + request.getNote() : ""));
        log.info("Review {} moderated -> {} by {}", reviewId, request.getStatus(), moderatorUsername);
        return ReviewResponse.of(review);
    }

    @Override
    @Transactional
    public ReviewReportResponse resolveReport(String moderatorUsername, Long reportId,
                                              boolean dismiss, String note) {
        ReviewReport report = reviewReportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Review report", reportId));
        if (report.getStatus() != ReportStatus.OPEN) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Report is not OPEN (current: " + report.getStatus() + ")");
        }
        report.setStatus(dismiss ? ReportStatus.DISMISSED : ReportStatus.RESOLVED);
        report.setModeratorNote(note);
        auditService.record(AuditActions.REVIEW_REPORT_RESOLVE, "ReviewReport", reportId,
                (dismiss ? "Dismissed" : "Resolved") + " by " + moderatorUsername
                        + (note != null ? ": " + note : ""));
        return ReviewReportResponse.of(report);
    }
}
