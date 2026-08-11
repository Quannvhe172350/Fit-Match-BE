package com.fitmatch.service;

import com.fitmatch.common.enums.BookingStatus;
import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.ReportStatus;
import com.fitmatch.common.enums.ReviewStatus;
import com.fitmatch.dto.review.ModerateReviewRequest;
import com.fitmatch.dto.review.ReportRequest;
import com.fitmatch.dto.review.ReviewRequest;
import com.fitmatch.dto.review.ReviewResponse;
import com.fitmatch.entity.Booking;
import com.fitmatch.entity.GymProfile;
import com.fitmatch.entity.PtProfile;
import com.fitmatch.entity.Review;
import com.fitmatch.entity.ReviewReport;
import com.fitmatch.entity.User;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.BookingRepository;
import com.fitmatch.repository.ReviewReportRepository;
import com.fitmatch.repository.ReviewRepository;
import com.fitmatch.service.impl.ReviewServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceImplTest {

    @Mock private ReviewRepository reviewRepository;
    @Mock private ReviewReportRepository reviewReportRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private AuditService auditService;
    @Mock private com.fitmatch.service.support.NotificationDispatcher notificationDispatcher;
    // UC-008: mock refresh denorm rating (no-op trong unit test)
    @Mock private com.fitmatch.service.support.RatingAggregator ratingAggregator;
    // V64: ảnh đính kèm review — file thật nằm trên GCS, ở đây chỉ cần biết
    // ReviewService gọi đúng MediaService với đúng entityType/imageType.
    @Mock private MediaService mediaService;
    @Mock private com.fitmatch.repository.MediaAssetRepository mediaRepository;
    @InjectMocks private ReviewServiceImpl service;

    private Booking completedBooking() {
        return Booking.builder().id(10L)
                .customer(User.builder().username("john").build())
                .gymProfile(GymProfile.builder().id(5L).gymName("Gym A").build())
                .status(BookingStatus.COMPLETED)
                .build();
    }

    @Test
    void create_completedBooking_savesVisibleReview() {
        when(bookingRepository.findByIdAndCustomer_Username(10L, "john"))
                .thenReturn(Optional.of(completedBooking()));
        when(reviewRepository.existsByBooking_Id(10L)).thenReturn(false);
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> {
            Review r = inv.getArgument(0);
            r.setId(1L);
            return r;
        });

        ReviewResponse res = service.create("john", new ReviewRequest(10L, 5, "great", null));

        assertThat(res.getRating()).isEqualTo(5);
        assertThat(res.getStatus()).isEqualTo(ReviewStatus.VISIBLE);
    }

    @Test
    void create_bookingWithPt_ratesGymAndPt() {
        // UC-069: chá»‰ cháº¥m Ä‘iá»ƒm PT khi khÃ¡ch thá»±c sá»± Ä‘áº·t buá»•i cÃ³ PT Ä‘Ã³.
        Booking b = completedBooking();
        b.setPtProfile(PtProfile.builder().id(7L).displayName("Coach T").build());
        when(bookingRepository.findByIdAndCustomer_Username(10L, "john")).thenReturn(Optional.of(b));
        when(reviewRepository.existsByBooking_Id(10L)).thenReturn(false);
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));

        ReviewResponse res = service.create("john", new ReviewRequest(10L, 4, "good pt", null));

        assertThat(res.getPtProfileId()).isEqualTo(7L);
        verify(ratingAggregator).refreshGym(5L);
        verify(ratingAggregator).refreshPt(7L);
    }

    @Test
    void create_bookingWithoutPt_ratesGymOnly() {
        when(bookingRepository.findByIdAndCustomer_Username(10L, "john"))
                .thenReturn(Optional.of(completedBooking()));
        when(reviewRepository.existsByBooking_Id(10L)).thenReturn(false);
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));

        ReviewResponse res = service.create("john", new ReviewRequest(10L, 4, "nice gym", null));

        assertThat(res.getPtProfileId()).isNull();
        verify(ratingAggregator).refreshGym(5L);
        verify(ratingAggregator).refreshPt(null);
    }

    @Test
    void create_bookingOfAnotherCustomer_throwsNotFound() {
        // Cháº·n Ä‘Ã¡nh giÃ¡ gym/PT chÆ°a tá»«ng Ä‘áº·t: booking pháº£i thuá»™c chÃ­nh khÃ¡ch.
        when(bookingRepository.findByIdAndCustomer_Username(10L, "mallory")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create("mallory", new ReviewRequest(10L, 5, "fake", null)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void create_notCompleted_throws() {
        Booking b = completedBooking();
        b.setStatus(BookingStatus.CONFIRMED);
        when(bookingRepository.findByIdAndCustomer_Username(10L, "john")).thenReturn(Optional.of(b));

        assertThatThrownBy(() -> service.create("john", new ReviewRequest(10L, 4, "x", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_STATE);
    }

    @Test
    void create_duplicate_throws() {
        when(bookingRepository.findByIdAndCustomer_Username(10L, "john"))
                .thenReturn(Optional.of(completedBooking()));
        when(reviewRepository.existsByBooking_Id(10L)).thenReturn(true);

        assertThatThrownBy(() -> service.create("john", new ReviewRequest(10L, 4, "x", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_STATE);
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void report_duplicateOpen_throws() {
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(Review.builder().id(1L).build()));
        when(reviewReportRepository.existsByReview_IdAndCreatedByAndStatus(1L, "bob", ReportStatus.OPEN))
                .thenReturn(true);

        assertThatThrownBy(() -> service.report("bob", 1L, new ReportRequest("spam")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_STATE);
    }

    @Test
    void moderate_setsStatusAndAudits() {
        Review review = Review.builder().id(1L).status(ReviewStatus.VISIBLE)
                .booking(completedBooking())
                .customer(User.builder().username("john").build())
                .gymProfile(GymProfile.builder().id(5L).gymName("Gym A").build())
                .rating(1).build();
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(review));

        ReviewResponse res = service.moderate("mod", 1L,
                new ModerateReviewRequest(ReviewStatus.HIDDEN, "offensive"));

        assertThat(res.getStatus()).isEqualTo(ReviewStatus.HIDDEN);
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.HIDDEN);
        verify(auditService).record(any(), any(), any(), any());
    }

    @Test
    void delete_withReports_softRemovesInsteadOfHardDelete() {
        // P0-0.4: review Ä‘ang bá»‹ report -> soft-delete (REMOVED), KHÃ”NG hard-delete (trÃ¡nh vi pháº¡m FK).
        Review review = Review.builder().id(1L).status(ReviewStatus.VISIBLE)
                .customer(User.builder().username("john").build())
                .booking(completedBooking()).rating(3).build();
        when(reviewRepository.findByIdAndCustomer_Username(1L, "john")).thenReturn(Optional.of(review));
        when(reviewReportRepository.existsByReview_Id(1L)).thenReturn(true);

        service.delete("john", 1L);

        assertThat(review.getStatus()).isEqualTo(ReviewStatus.REMOVED);
        verify(reviewRepository, never()).delete(any());
    }

    @Test
    void delete_withoutReports_hardDeletes() {
        Review review = Review.builder().id(1L).status(ReviewStatus.VISIBLE)
                .customer(User.builder().username("john").build())
                .booking(completedBooking()).rating(3).build();
        when(reviewRepository.findByIdAndCustomer_Username(1L, "john")).thenReturn(Optional.of(review));
        when(reviewReportRepository.existsByReview_Id(1L)).thenReturn(false);

        service.delete("john", 1L);

        verify(reviewRepository).delete(review);
    }

    @Test
    void moderate_removedReview_throws() {
        // P2-B9: REMOVED lÃ  tráº¡ng thÃ¡i cuá»‘i â€” khÃ´ng cho kiá»ƒm duyá»‡t Ä‘Æ°a ngÆ°á»£c láº¡i.
        Review review = Review.builder().id(1L).status(ReviewStatus.REMOVED)
                .booking(completedBooking())
                .customer(User.builder().username("john").build())
                .gymProfile(GymProfile.builder().id(5L).gymName("Gym A").build())
                .rating(1).build();
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> service.moderate("mod", 1L,
                new ModerateReviewRequest(ReviewStatus.VISIBLE, "restore")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_STATE);
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.REMOVED);
    }

    // ---------------- V64: ảnh đính kèm đánh giá ----------------

    @Test
    void create_withImages_attachesThemToTheReview() {
        when(bookingRepository.findByIdAndCustomer_Username(10L, "john"))
                .thenReturn(Optional.of(completedBooking()));
        when(reviewRepository.existsByBooking_Id(10L)).thenReturn(false);
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> {
            Review r = inv.getArgument(0);
            r.setId(77L);
            return r;
        });

        service.create("john", new ReviewRequest(10L, 5, "great", java.util.List.of(3L, 4L)));

        // Ảnh phải được gắn vào ĐÚNG review vừa tạo, dưới tên người gửi đánh giá —
        // đây là chốt chặn ngăn mượn ảnh của người khác.
        verify(mediaService).attach("john", com.fitmatch.common.enums.MediaEntityType.REVIEW, 77L,
                com.fitmatch.common.enums.MediaImageType.REVIEW_IMAGE, java.util.List.of(3L, 4L));
    }

    @Test
    void create_withoutImages_doesNotTouchMedia() {
        when(bookingRepository.findByIdAndCustomer_Username(10L, "john"))
                .thenReturn(Optional.of(completedBooking()));
        when(reviewRepository.existsByBooking_Id(10L)).thenReturn(false);
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create("john", new ReviewRequest(10L, 5, "great", null));

        verify(mediaService, never()).attach(any(), any(), any(), any(), any());
    }

    @Test
    void update_removingAnImage_deletesItFromStorage() {
        // mediaIds là trạng thái CUỐI CÙNG: ảnh không còn trong danh sách bị xoá hẳn.
        Review review = Review.builder().id(1L).status(ReviewStatus.VISIBLE)
                .customer(User.builder().username("john").build())
                .booking(completedBooking())
                .gymProfile(GymProfile.builder().id(5L).gymName("Gym A").build())
                .rating(3).build();
        when(reviewRepository.findByIdAndCustomer_Username(1L, "john")).thenReturn(Optional.of(review));
        when(mediaRepository.findByEntityTypeAndEntityIdAndImageTypeOrderBySortOrderAscIdAsc(
                com.fitmatch.common.enums.MediaEntityType.REVIEW, 1L,
                com.fitmatch.common.enums.MediaImageType.REVIEW_IMAGE))
                .thenReturn(java.util.List.of(
                        com.fitmatch.entity.MediaAsset.builder().id(8L).build(),
                        com.fitmatch.entity.MediaAsset.builder().id(9L).build()));

        service.update("john", 1L, new ReviewRequest(10L, 4, "edited", java.util.List.of(9L)));

        verify(mediaService).delete("john", 8L);
        verify(mediaService, never()).delete("john", 9L);
    }

    @Test
    void delete_withoutReports_alsoRemovesAttachedImages() {
        // Xoá hẳn review mà giữ ảnh lại = file mồ côi trên GCS, trả tiền lưu trữ mãi.
        Review review = Review.builder().id(1L).status(ReviewStatus.VISIBLE)
                .customer(User.builder().username("john").build())
                .booking(completedBooking()).rating(3).build();
        when(reviewRepository.findByIdAndCustomer_Username(1L, "john")).thenReturn(Optional.of(review));
        when(reviewReportRepository.existsByReview_Id(1L)).thenReturn(false);

        service.delete("john", 1L);

        verify(mediaService).deleteAllForEntity(com.fitmatch.common.enums.MediaEntityType.REVIEW, 1L);
    }

    @Test
    void gymRatingSummary_returnsAverageAndDistribution() {
        when(ratingAggregator.forGym(5L)).thenReturn(
                new com.fitmatch.service.support.RatingAggregator.Rating(
                        new java.math.BigDecimal("4.3"), 10L));
        when(ratingAggregator.distributionForGym(5L))
                .thenReturn(java.util.Map.of(5, 6L, 4, 2L, 3, 1L, 1, 1L));

        var summary = service.gymRatingSummary(5L);

        assertThat(summary.getAverageRating()).isEqualByComparingTo("4.3");
        assertThat(summary.getTotalReviews()).isEqualTo(10L);
        assertThat(summary.getRating5Count()).isEqualTo(6L);
        assertThat(summary.getRating4Count()).isEqualTo(2L);
        // Mức sao không ai chấm phải là 0, không phải null/lỗi.
        assertThat(summary.getRating2Count()).isZero();
    }

    @Test
    void resolveReport_openReport_marksResolved() {
        ReviewReport report = ReviewReport.builder().id(2L).status(ReportStatus.OPEN)
                .reason("spam")
                .review(Review.builder().id(1L).booking(completedBooking())
                        .customer(User.builder().username("john").build())
                        .gymProfile(GymProfile.builder().id(5L).gymName("Gym A").build())
                        .rating(1).build())
                .build();
        when(reviewReportRepository.findById(2L)).thenReturn(Optional.of(report));

        var res = service.resolveReport("mod", 2L, false, "handled");

        assertThat(res.getStatus()).isEqualTo(ReportStatus.RESOLVED);
    }
}
