package com.fitmatch.service;

import com.fitmatch.entity.GymBranch;
import com.fitmatch.entity.GymProfile;
import com.fitmatch.entity.Ticket;
import com.fitmatch.entity.TicketType;
import com.fitmatch.entity.TrainingSession;
import com.fitmatch.entity.User;
import com.fitmatch.service.support.NotificationDispatcher;
import com.fitmatch.service.support.NotificationEvent;
import com.fitmatch.service.support.NotificationTemplateResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Đường dẫn trong thông báo phải là route CÓ THẬT của FE.
 *
 * <p>Hộp thư render thẳng {@code <Link href={item.link}>}, nên một đường dẫn
 * chết là cú 404 giáng vào đúng người vừa nhận thông báo — và không có gì trong
 * build canh nó: đổi route ở FE hay xoá cả một mô hình (booking -> vé) đều biên
 * dịch sạch, mọi test service vẫn xanh. Đó là cách {@code /tickets/{id}},
 * {@code /sessions/{id}}, {@code /gym/tickets}, {@code /gym/profile} và
 * {@code /gym/leave-requests} sống sót trong thông báo sau khi trang tương ứng
 * đã bị xoá.
 *
 * <p>KHI FE ĐỔI ROUTE: cập nhật {@link #FE_ROUTES} cho khớp
 * {@code Fit-Match-FE/src/app}. Danh sách này là bản khai báo có chủ ý, không
 * phải bản sao tự sinh.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationDispatcherLinkTest {

    /** Route thật của FE, đối chiếu ngày 23/08/2026 với src/app/**\/page.tsx. */
    private static final Set<String> FE_ROUTES = Set.of(
            "/schedule", "/checkout", "/notifications",
            "/profile/tickets", "/profile/wallet", "/profile/reviews", "/profile/disputes",
            "/gym", "/gym/verification", "/gym/settings", "/gym/pts", "/gym/schedule",
            "/gym/calendar", "/gym/wallet", "/gym/disputes",
            "/trainer/sessions", "/trainer/availability", "/trainer/disputes");

    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private NotificationTemplateResolver templateResolver;
    private NotificationDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        when(templateResolver.render(any(), any(), any(), any()))
                .thenAnswer(inv -> new NotificationTemplateResolver.Rendered(
                        inv.getArgument(1), inv.getArgument(2)));
        dispatcher = new NotificationDispatcher(eventPublisher, templateResolver);
    }

    /** Mọi link phát ra phải là một route FE, kèm query string nếu cần. */
    @Test
    void everyTicketAndSessionLink_pointsAtRealFeRoute() {
        Ticket ticket = ticket();
        TrainingSession session = session(ticket);

        dispatcher.ticketPaid(ticket);
        dispatcher.ticketPaymentExpired(ticket);
        dispatcher.ticketPaymentFailed(ticket, "thiếu tiền");
        dispatcher.ticketExpiringSoon(ticket, 3);
        dispatcher.ticketExpired(ticket);
        dispatcher.ticketUsedUp(ticket);
        dispatcher.ticketRefundExecuted(ticket, BigDecimal.TEN, 2);
        dispatcher.ticketRefundRejected(ticket, BigDecimal.TEN, "hết cửa sổ hoàn");
        dispatcher.ticketSettlementReleased(ticket, BigDecimal.TEN);
        dispatcher.sessionBooked(session);
        dispatcher.sessionRescheduled(session, LocalDate.of(2026, 8, 1));
        dispatcher.sessionPtAssigned(session);
        dispatcher.ptSessionConfirmed(session);
        dispatcher.ptSuspendedAffectsSession(session, "đình chỉ");
        dispatcher.sessionPtCancelled(session, "Coach", LocalTime.of(7, 0), BigDecimal.TEN);
        dispatcher.sessionDoneReviewPt(sessionWithPt(ticket));

        for (String link : publishedLinks()) {
            assertThat(routeOf(link))
                    .as("link %s phải trỏ vào một route FE có thật", link)
                    .isIn(FE_ROUTES);
        }
    }

    /**
     * Vé vừa dùng được thì việc tiếp theo là chọn ngày — mở thẳng chế độ đặt lịch
     * của ĐÚNG vé đó, đừng bắt khách tự tìm lại vé trong danh sách.
     */
    @Test
    void activatedTicket_deepLinksIntoBookingModeOfThatTicket() {
        dispatcher.ticketPaid(ticket());

        assertThat(publishedLinks()).contains("/schedule?ticketId=7&mode=book");
    }

    /** Buổi tập không có trang riêng: mọi thông báo cấp buổi về lịch đặt. */
    @Test
    void sessionLinks_allGoToSchedule() {
        TrainingSession session = session(ticket());

        dispatcher.ptSessionConfirmed(session);
        dispatcher.ptSuspendedAffectsSession(session, "đình chỉ");
        dispatcher.sessionPtCancelled(session, "Coach", LocalTime.of(7, 0), BigDecimal.TEN);

        assertThat(publishedLinks()).containsOnly("/schedule");
    }

    /**
     * Buổi tự tập không có ai để chấm: không mời đánh giá HLV. Guard nằm trong
     * dispatcher, nên phải kiểm ở đây — chỗ gọi (TicketMaintenanceService) mock
     * dispatcher nên không thấy được nhánh này.
     */
    @Test
    void sessionWithoutPt_doesNotInviteReview() {
        dispatcher.sessionDoneReviewPt(session(ticket()));

        org.mockito.Mockito.verify(eventPublisher, org.mockito.Mockito.never())
                .publishEvent(org.mockito.ArgumentMatchers.any(Object.class));
    }

    /**
     * Ngoại lệ có chủ ý của quy ước "thông báo cấp buổi tập về /schedule": lời
     * mời đánh giá phải rơi xuống chỗ ĐÁNH GIÁ ĐƯỢC. Thả khách xuống lịch tháng
     * là bắt họ tự nhớ ra buổi nào rồi đi tìm ô ngày đúng, trong khi
     * /profile/reviews có sẵn danh sách buổi đang chờ chấm.
     */
    @Test
    void reviewInvites_landOnTheReviewPage() {
        dispatcher.sessionDoneReviewPt(sessionWithPt(ticket()));
        dispatcher.ticketUsedUp(ticket());

        assertThat(publishedLinks()).containsOnly("/profile/reviews");
    }

    // ---------- hạ tầng ----------

    private List<String> publishedLinks() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(captor.capture());
        return captor.getAllValues().stream()
                .map(NotificationEvent.class::cast)
                .map(NotificationEvent::link)
                .toList();
    }

    /** Bỏ query string để so với danh sách route. */
    private static String routeOf(String link) {
        int q = link.indexOf('?');
        return q < 0 ? link : link.substring(0, q);
    }

    private static User user(String username) {
        User u = new User();
        u.setUsername(username);
        u.setFullName("Người " + username);
        return u;
    }

    private static Ticket ticket() {
        GymProfile gym = new GymProfile();
        gym.setUser(user("gym"));
        GymBranch branch = new GymBranch();
        branch.setName("Chi nhánh Quận 1");
        TicketType type = new TicketType();
        type.setName("Vé gói 10 ngày");
        return Ticket.builder()
                .id(7L)
                .customer(user("khach"))
                .ticketType(type)
                .gymProfile(gym)
                .gymBranch(branch)
                .dayCount(10)
                .payableAmount(BigDecimal.valueOf(1_000_000))
                .expiresAt(LocalDateTime.of(2026, 9, 30, 23, 59))
                .build();
    }

    /** Buổi có HLV — mời đánh giá HLV chỉ gửi khi buổi thật sự có người dạy. */
    private static TrainingSession sessionWithPt(Ticket ticket) {
        TrainingSession s = session(ticket);
        com.fitmatch.entity.PtProfile pt = new com.fitmatch.entity.PtProfile();
        pt.setId(9L);
        pt.setDisplayName("Coach Minh");
        pt.setUser(user("pt"));
        s.setPtProfile(pt);
        return s;
    }

    private static TrainingSession session(Ticket ticket) {
        TrainingSession s = new TrainingSession();
        s.setId(42L);
        s.setTicket(ticket);
        s.setSessionDate(LocalDate.of(2026, 8, 25));
        s.setPtSlotStart(LocalTime.of(7, 0));
        return s;
    }
}
