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

        ReviewResponse res = service.create("john", new ReviewRequest(10L, 5, "great"));

        assertThat(res.getRating()).isEqualTo(5);
        assertThat(res.getStatus()).isEqualTo(ReviewStatus.VISIBLE);
    }

    @Test
    void create_bookingWithPt_ratesGymAndPt() {
        // UC-069: chỉ chấm điểm PT khi khách thực sự đặt buổi có PT đó.
        Booking b = completedBooking();
        b.setPtProfile(PtProfile.builder().id(7L).displayName("Coach T").build());
        when(bookingRepository.findByIdAndCustomer_Username(10L, "john")).thenReturn(Optional.of(b));
        when(reviewRepository.existsByBooking_Id(10L)).thenReturn(false);
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));

        ReviewResponse res = service.create("john", new ReviewRequest(10L, 4, "good pt"));

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

        ReviewResponse res = service.create("john", new ReviewRequest(10L, 4, "nice gym"));

        assertThat(res.getPtProfileId()).isNull();
        verify(ratingAggregator).refreshGym(5L);
        verify(ratingAggregator).refreshPt(null);
    }

    @Test
    void create_bookingOfAnotherCustomer_throwsNotFound() {
        // Chặn đánh giá gym/PT chưa từng đặt: booking phải thuộc chính khách.
        when(bookingRepository.findByIdAndCustomer_Username(10L, "mallory")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create("mallory", new ReviewRequest(10L, 5, "fake")))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void create_notCompleted_throws() {
        Booking b = completedBooking();
        b.setStatus(BookingStatus.CONFIRMED);
        when(bookingRepository.findByIdAndCustomer_Username(10L, "john")).thenReturn(Optional.of(b));

        assertThatThrownBy(() -> service.create("john", new ReviewRequest(10L, 4, "x")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_STATE);
    }

    @Test
    void create_duplicate_throws() {
        when(bookingRepository.findByIdAndCustomer_Username(10L, "john"))
                .thenReturn(Optional.of(completedBooking()));
        when(reviewRepository.existsByBooking_Id(10L)).thenReturn(true);

        assertThatThrownBy(() -> service.create("john", new ReviewRequest(10L, 4, "x")))
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
        // P0-0.4: review đang bị report -> soft-delete (REMOVED), KHÔNG hard-delete (tránh vi phạm FK).
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
        // P2-B9: REMOVED là trạng thái cuối — không cho kiểm duyệt đưa ngược lại.
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
