package com.fitmatch.service;

import com.fitmatch.common.enums.ReviewTargetType;
import com.fitmatch.common.enums.SessionStatus;
import com.fitmatch.common.enums.TicketStatus;
import com.fitmatch.dto.review.ReviewResponse;
import com.fitmatch.dto.review.TicketReviewRequest;
import com.fitmatch.entity.GymProfile;
import com.fitmatch.entity.PtProfile;
import com.fitmatch.entity.Review;
import com.fitmatch.entity.Ticket;
import com.fitmatch.entity.TicketType;
import com.fitmatch.entity.TrainingSession;
import com.fitmatch.entity.User;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.repository.MediaAssetRepository;
import com.fitmatch.repository.ReviewRepository;
import com.fitmatch.repository.TicketRepository;
import com.fitmatch.repository.TrainingSessionRepository;
import com.fitmatch.service.impl.ReviewServiceImpl;
import com.fitmatch.service.support.RatingAggregator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Câu 17 + 36: đánh giá gym mở khi vé USED_UP, đánh giá PT mở khi buổi DONE và
 * có PT. Mỗi thứ đúng một lần.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TicketReviewEligibilityTest {

    private static final String USERNAME = "customer1";
    private static final Long TICKET_ID = 100L;
    private static final Long SESSION_ID = 500L;

    @Mock private ReviewRepository reviewRepository;
    @Mock private com.fitmatch.repository.ReviewReportRepository reviewReportRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private TrainingSessionRepository trainingSessionRepository;
    @Mock private AuditService auditService;
    @Mock private com.fitmatch.service.support.NotificationDispatcher notificationDispatcher;
    @Mock private RatingAggregator ratingAggregator;
    @Mock private MediaService mediaService;
    @Mock private MediaAssetRepository mediaRepository;
    @InjectMocks private ReviewServiceImpl service;

    private final TicketReviewRequest request = TicketReviewRequest.builder()
            .rating(5).comment("Rất tốt").build();

    @BeforeEach
    void setUp() {
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> {
            Review r = inv.getArgument(0);
            if (r.getId() == null) r.setId(1L);
            return r;
        });
        when(mediaRepository.findByEntityTypeAndEntityIdOrderBySortOrderAscIdAsc(any(), anyLong()))
                .thenReturn(List.of());
        when(ratingAggregator.forGym(anyLong()))
                .thenReturn(new RatingAggregator.Rating(java.math.BigDecimal.valueOf(5), 1));
        when(ratingAggregator.forPt(anyLong()))
                .thenReturn(new RatingAggregator.Rating(java.math.BigDecimal.valueOf(5), 1));
    }

    private Ticket ticket(TicketStatus status) {
        Ticket t = Ticket.builder()
                .id(TICKET_ID)
                .customer(User.builder().id(9L).username(USERNAME).build())
                .ticketType(TicketType.builder().id(33L).name("Gói").build())
                .gymProfile(GymProfile.builder().id(1L).gymName("Gym A").build())
                .dayCount(3).status(status)
                .build();
        when(ticketRepository.findByIdAndCustomer_Username(TICKET_ID, USERNAME))
                .thenReturn(Optional.of(t));
        return t;
    }

    private TrainingSession session(Ticket t, SessionStatus status, PtProfile pt) {
        TrainingSession s = TrainingSession.builder()
                .id(SESSION_ID).ticket(t).status(status).ptProfile(pt)
                .sessionDate(LocalDate.now().minusDays(1)).dayIndex(1)
                .build();
        when(trainingSessionRepository.findByIdAndTicket_Customer_Username(SESSION_ID, USERNAME))
                .thenReturn(Optional.of(s));
        return s;
    }

    // ---------- đánh giá phòng gym ----------

    @Test
    void gymReview_onUsedUpTicket_isAllowed() {
        ticket(TicketStatus.USED_UP);

        ReviewResponse response = service.reviewGym(USERNAME, TICKET_ID, request);

        assertThat(response.getTargetType()).isEqualTo(ReviewTargetType.GYM);
        assertThat(response.getTicketId()).isEqualTo(TICKET_ID);
        assertThat(response.getSessionId()).isNull();
    }

    @Test
    void gymReview_onActiveTicket_isRejected() {
        ticket(TicketStatus.ACTIVE);

        assertThatThrownBy(() -> service.reviewGym(USERNAME, TICKET_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("dùng hết vé");
    }

    @Test
    void gymReview_twice_isRejected() {
        ticket(TicketStatus.USED_UP);
        when(reviewRepository.existsByTicket_Id(TICKET_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.reviewGym(USERNAME, TICKET_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã được đánh giá");
    }

    // ---------- đánh giá PT ----------

    @Test
    void ptReview_onDoneSessionWithPt_isAllowed() {
        Ticket t = ticket(TicketStatus.ACTIVE);
        session(t, SessionStatus.DONE, PtProfile.builder().id(11L).displayName("PT A").build());

        ReviewResponse response = service.reviewPt(USERNAME, SESSION_ID, request);

        assertThat(response.getTargetType()).isEqualTo(ReviewTargetType.PT);
        assertThat(response.getSessionId()).isEqualTo(SESSION_ID);
        assertThat(response.getPtProfileId()).isEqualTo(11L);
    }

    /** Đánh giá PT KHÔNG cần đợi dùng hết vé — mở ngay khi buổi đó xong. */
    @Test
    void ptReview_doesNotRequireUsedUpTicket() {
        Ticket t = ticket(TicketStatus.ACTIVE);
        session(t, SessionStatus.DONE, PtProfile.builder().id(11L).displayName("PT A").build());

        assertThat(service.reviewPt(USERNAME, SESSION_ID, request)).isNotNull();
    }

    @Test
    void ptReview_onScheduledSession_isRejected() {
        Ticket t = ticket(TicketStatus.ACTIVE);
        session(t, SessionStatus.SCHEDULED, PtProfile.builder().id(11L).build());

        assertThatThrownBy(() -> service.reviewPt(USERNAME, SESSION_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("kết thúc");
    }

    @Test
    void ptReview_onSessionWithoutPt_isRejected() {
        Ticket t = ticket(TicketStatus.ACTIVE);
        session(t, SessionStatus.DONE, null);

        assertThatThrownBy(() -> service.reviewPt(USERNAME, SESSION_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không có PT");
    }

    @Test
    void ptReview_twice_isRejected() {
        Ticket t = ticket(TicketStatus.ACTIVE);
        session(t, SessionStatus.DONE, PtProfile.builder().id(11L).build());
        when(reviewRepository.existsBySession_Id(SESSION_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.reviewPt(USERNAME, SESSION_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã được đánh giá");
    }
}
