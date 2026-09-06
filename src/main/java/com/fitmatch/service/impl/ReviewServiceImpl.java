package com.fitmatch.service.impl;

import com.fitmatch.common.AuditActions;
import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.MediaEntityType;
import com.fitmatch.common.enums.MediaImageType;
import com.fitmatch.common.enums.ReportStatus;
import com.fitmatch.common.enums.ReviewStatus;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.media.MediaResponse;
import com.fitmatch.dto.review.ModerateReviewRequest;
import com.fitmatch.dto.review.RatingSummaryResponse;
import com.fitmatch.dto.review.ReportRequest;
import com.fitmatch.dto.review.ReviewReportResponse;
import com.fitmatch.dto.review.ReviewResponse;
import com.fitmatch.entity.MediaAsset;
import com.fitmatch.entity.Review;
import com.fitmatch.entity.ReviewReport;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.MediaAssetRepository;
import com.fitmatch.repository.ReviewReportRepository;
import com.fitmatch.repository.ReviewRepository;
import com.fitmatch.service.AuditService;
import com.fitmatch.service.MediaService;
import com.fitmatch.service.ReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService, com.fitmatch.service.TicketReviewService {

    private final ReviewRepository reviewRepository;
    private final ReviewReportRepository reviewReportRepository;
    private final com.fitmatch.repository.TicketRepository ticketRepository;
    private final com.fitmatch.repository.TrainingSessionRepository trainingSessionRepository;
    private final AuditService auditService;
    private final com.fitmatch.service.support.NotificationDispatcher notificationDispatcher;
    // UC-008 (V51): đồng bộ cột denorm avg_rating/rating_count sau mỗi thay đổi review
    private final com.fitmatch.service.support.RatingAggregator ratingAggregator;
    // V64: ảnh đính kèm đánh giá — file nằm trên GCS, ở đây chỉ gắn/gỡ metadata.
    private final MediaService mediaService;
    private final MediaAssetRepository mediaRepository;

    private void refreshDenormRating(Review review) {
        ratingAggregator.refreshGym(review.getGymProfile() != null ? review.getGymProfile().getId() : null);
        ratingAggregator.refreshPt(review.getPtProfile() != null ? review.getPtProfile().getId() : null);
    }

    // ------------------------------------------------------------------
    // Ảnh đính kèm (V64)
    // ------------------------------------------------------------------

    /** Ảnh của một review, theo đúng thứ tự khách đã sắp. */
    private List<MediaResponse> imagesOf(Long reviewId) {
        return mediaRepository
                .findByEntityTypeAndEntityIdAndImageTypeOrderBySortOrderAscIdAsc(
                        MediaEntityType.REVIEW, reviewId, MediaImageType.REVIEW_IMAGE)
                .stream().map(mediaService::toResponse).toList();
    }

    /**
     * Nạp ảnh cho cả một trang review trong MỘT truy vấn rồi ghép lại — gọi
     * {@link #imagesOf} cho từng dòng sẽ thành N+1 ngay trên trang chi tiết gym.
     */
    private PageResponse<ReviewResponse> toPage(Page<Review> page) {
        List<Long> ids = page.getContent().stream().map(Review::getId).toList();
        Map<Long, List<MediaResponse>> images = mediaService.listForEntities(
                MediaEntityType.REVIEW, ids, MediaImageType.REVIEW_IMAGE);
        return PageResponse.of(page, r -> ReviewResponse.of(r, images.get(r.getId())));
    }

    /**
     * Đồng bộ ảnh của review với danh sách id client gửi lên.
     *
     * <p>{@code mediaIds} là trạng thái cuối cùng chứ không phải "thêm vào": ảnh
     * đang gắn mà vắng mặt trong danh sách được xoá hẳn (cả object trên GCS), nên
     * bỏ ảnh khỏi form là thật sự xoá chứ không để lại rác. {@code null} = không
     * đụng tới ảnh (client cũ chỉ sửa nội dung vẫn chạy đúng).
     */
    private void syncImages(String username, Review review, List<Long> mediaIds) {
        if (mediaIds == null) return;
        List<MediaAsset> current = mediaRepository
                .findByEntityTypeAndEntityIdAndImageTypeOrderBySortOrderAscIdAsc(
                        MediaEntityType.REVIEW, review.getId(), MediaImageType.REVIEW_IMAGE);
        for (MediaAsset existing : current) {
            if (!mediaIds.contains(existing.getId())) {
                mediaService.delete(username, existing.getId());
            }
        }
        mediaService.attach(username, MediaEntityType.REVIEW, review.getId(),
                MediaImageType.REVIEW_IMAGE, mediaIds);
    }

    // ------------------------------------------------------------------
    // Mô hình vé (câu 17 + 36): hai loại đánh giá, hai mốc mở khác nhau
    // ------------------------------------------------------------------

    /**
     * Câu 17: đánh giá phòng gym mở khi khách đã DÙNG HẾT vé — chấm điểm cả trải
     * nghiệm chứ không phải từng buổi lẻ. Vé còn ngày chưa dùng thì chưa đủ cơ sở.
     */
    @Override
    @Transactional
    public ReviewResponse reviewGym(String customerUsername, Long ticketId,
                                    com.fitmatch.dto.review.TicketReviewRequest request) {
        com.fitmatch.entity.Ticket ticket = ticketRepository
                .findByIdAndCustomer_Username(ticketId, customerUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));
        if (ticket.getStatus() != com.fitmatch.common.enums.TicketStatus.USED_UP) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Chỉ đánh giá phòng gym khi đã dùng hết vé (hiện: " + ticket.getStatus() + ")");
        }
        if (reviewRepository.existsByTicket_Id(ticketId)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Vé này đã được đánh giá");
        }
        Review review = reviewRepository.save(Review.builder()
                .targetType(com.fitmatch.common.enums.ReviewTargetType.GYM)
                .ticket(ticket)
                .customer(ticket.getCustomer())
                .gymProfile(ticket.getGymProfile())
                .rating(request.getRating())
                .comment(request.getComment())
                .status(ReviewStatus.VISIBLE)
                .build());
        syncImages(customerUsername, review, request.getMediaIds());
        refreshDenormRating(review);
        log.info("Gym review {} created for ticket {} (rating {})",
                review.getId(), ticketId, request.getRating());
        return ReviewResponse.of(review, imagesOf(review.getId()));
    }

    /**
     * Câu 36: đánh giá PT mở ngay khi BUỔI đó xong — người tập còn nhớ rõ. Buổi
     * không có PT thì không có gì để chấm.
     */
    @Override
    @Transactional
    public ReviewResponse reviewPt(String customerUsername, Long sessionId,
                                   com.fitmatch.dto.review.TicketReviewRequest request) {
        com.fitmatch.entity.TrainingSession session = trainingSessionRepository
                .findByIdAndTicket_Customer_Username(sessionId, customerUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Training session", sessionId));
        if (session.getPtProfile() == null) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Buổi tập này không có PT để đánh giá");
        }
        if (session.getStatus() != com.fitmatch.common.enums.SessionStatus.DONE) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Chỉ đánh giá PT sau khi buổi tập kết thúc (hiện: " + session.getStatus() + ")");
        }
        if (reviewRepository.existsBySession_Id(sessionId)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Buổi tập này đã được đánh giá");
        }
        com.fitmatch.entity.Ticket ticket = session.getTicket();
        Review review = reviewRepository.save(Review.builder()
                .targetType(com.fitmatch.common.enums.ReviewTargetType.PT)
                .session(session)
                .customer(ticket.getCustomer())
                // gymProfile vẫn được set để review PT hiện đúng ngữ cảnh phòng tập;
                // điểm trung bình của gym chỉ tính review GYM nên không bị nhiễu.
                .gymProfile(ticket.getGymProfile())
                .ptProfile(session.getPtProfile())
                .rating(request.getRating())
                .comment(request.getComment())
                .status(ReviewStatus.VISIBLE)
                .build());
        syncImages(customerUsername, review, request.getMediaIds());
        refreshDenormRating(review);
        log.info("PT review {} created for session {} (rating {})",
                review.getId(), sessionId, request.getRating());
        return ReviewResponse.of(review, imagesOf(review.getId()));
    }

    @Override
    @Transactional
    public ReviewResponse update(String customerUsername, Long reviewId, com.fitmatch.dto.review.TicketReviewRequest request) {
        Review review = reviewRepository.findByIdAndCustomer_Username(reviewId, customerUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Review", reviewId));
        if (review.getStatus() == ReviewStatus.REMOVED) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "A removed review cannot be edited");
        }
        review.setRating(request.getRating());
        review.setComment(request.getComment());
        syncImages(customerUsername, review, request.getMediaIds());
        refreshDenormRating(review);
        return ReviewResponse.of(review, imagesOf(review.getId()));
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
        // Xoá hẳn review thì ảnh đính kèm cũng phải biến mất khỏi GCS — giữ lại chỉ
        // tạo file mồ côi vì không còn bản ghi nào trỏ tới.
        mediaService.deleteAllForEntity(MediaEntityType.REVIEW, reviewId);
        reviewRepository.delete(review);
        refreshDenormRating(review);
        log.info("Review {} deleted by owner {}", reviewId, customerUsername);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> myReviews(String customerUsername,
                                                  com.fitmatch.common.enums.ReviewTargetType targetType,
                                                  Pageable pageable) {
        return toPage(targetType == null
                ? reviewRepository.findByCustomer_UsernameOrderByIdDesc(customerUsername, pageable)
                : reviewRepository.findMineByType(customerUsername, targetType, pageable));
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
        // Chỉ đánh giá GYM: đánh giá PT có gymProfile nhưng thuộc về trang PT.
        return toPage(reviewRepository.findVisibleGymReviews(
                gymProfileId, ReviewStatus.VISIBLE, pageable));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> visibleForPt(Long ptProfileId, Pageable pageable) {
        return toPage(reviewRepository.findByPtProfile_IdAndStatusOrderByIdDesc(
                ptProfileId, ReviewStatus.VISIBLE, pageable));
    }

    @Override
    @Transactional(readOnly = true)
    public RatingSummaryResponse gymRatingSummary(Long gymProfileId) {
        var rating = ratingAggregator.forGym(gymProfileId);
        return RatingSummaryResponse.of("GYM", gymProfileId, rating.average(), rating.count(),
                ratingAggregator.distributionForGym(gymProfileId));
    }

    @Override
    @Transactional(readOnly = true)
    public RatingSummaryResponse ptRatingSummary(Long ptProfileId) {
        var rating = ratingAggregator.forPt(ptProfileId);
        return RatingSummaryResponse.of("PT", ptProfileId, rating.average(), rating.count(),
                ratingAggregator.distributionForPt(ptProfileId));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> gymReviews(String gymUsername,
                                                   com.fitmatch.common.enums.ReviewTargetType targetType,
                                                   Pageable pageable) {
        return toPage(targetType == null
                ? reviewRepository.findByGymProfile_User_UsernameOrderByIdDesc(gymUsername, pageable)
                : reviewRepository.findOwnedByType(gymUsername, targetType, pageable));
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
        return ReviewResponse.of(review, imagesOf(reviewId));
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
