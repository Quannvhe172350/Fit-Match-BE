package com.fitmatch.service.support;

import com.fitmatch.common.enums.NotificationCategory;
import com.fitmatch.entity.Dispute;
import com.fitmatch.entity.GymProfile;
import com.fitmatch.entity.Review;
import com.fitmatch.entity.Ticket;
import com.fitmatch.entity.TrainingSession;
import com.fitmatch.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Sinh thông báo chuẩn hoá theo sự kiện (UC-075) và giải quyết người nhận từ
  * vé/buổi tập/tranh chấp/đánh giá. Publish {@link NotificationEvent} để gửi SAU KHI giao
 * dịch nghiệp vụ commit (tránh "thông báo ma" khi rollback) — xem
 * {@link NotificationEventListener}. Mọi lỗi gửi được nuốt ở tầng NotificationService.
 *
 * UC-075 (V52): mỗi sự kiện có CODE khớp bảng notification_templates — admin chỉnh
 * title/body qua /api/admin/notification-templates; văn bản trong code là mặc định
 * (fallback khi template tắt/thiếu). Đổi placeholder ở đây thì cập nhật cột
 * placeholders trong migration seed cho khớp.
 */
@Component
@RequiredArgsConstructor
public class NotificationDispatcher {

    private final ApplicationEventPublisher eventPublisher;
    private final NotificationTemplateResolver templateResolver;

    /**
     * Đưa yêu cầu gửi vào hàng đợi AFTER_COMMIT. Lấy username NGAY (trong giao
     * dịch, session còn mở -> lazy-safe) rồi mang theo trong event.
     */
    private void dispatch(String code, User user, NotificationCategory category,
                          String defaultTitle, String defaultBody, String link,
                          Map<String, String> vars) {
        if (user == null) return;
        var rendered = templateResolver.render(code, defaultTitle, defaultBody, vars);
        eventPublisher.publishEvent(new NotificationEvent(
                user.getUsername(), category, rendered.title(), rendered.body(), link));
    }

    private static String s(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    /**
     * UC-062: kết quả xử lý yêu cầu rút tiền — chủ ví không còn phải tự vào kiểm tra.
     * V61: {@code link} do bên gọi truyền vì trang xem lệnh rút khác nhau theo
     * loại chủ ví (gym / PT / khách hàng); trước đây hằng số "/gym/withdrawals"
     * còn trỏ vào một route không tồn tại.
     */
    public void withdrawalDecided(User owner, Long withdrawalId, String decision, String detail, String link) {
        dispatch("WITHDRAWAL_DECIDED", owner, NotificationCategory.PAYMENT,
                "Yêu cầu rút tiền #" + withdrawalId + " " + decision,
                detail,
                link,
                Map.of("withdrawalId", s(withdrawalId), "decision", s(decision), "detail", s(detail)));
    }

    /** V61: khách hàng nhận tiền hoàn vào ví thay vì chờ chuyển khoản tay. */
    public void refundCreditedToWallet(User customer, java.math.BigDecimal amount, Long ticketId) {
        dispatch("REFUND_CREDITED_TO_WALLET", customer, NotificationCategory.PAYMENT,
                "Đã hoàn " + amount + " đ vào ví",
                "Tiền hoàn của vé #" + ticketId + " đã vào ví của bạn — "
                        + "có thể tạo lệnh rút về tài khoản ngân hàng bất cứ lúc nào.",
                "/profile/wallet",
                Map.of("amount", s(amount), "ticketId", s(ticketId)));
    }

    // ----- Gym verification (UC-013/014) -----

    /** UC-013/014: thông báo cho Gym Operator kết quả duyệt/đình chỉ hồ sơ. */
    public void gymVerificationDecided(GymProfile profile, String decision, String note) {
        if (profile == null) return;
        dispatch("GYM_VERIFICATION_DECIDED", profile.getUser(), NotificationCategory.ACCOUNT,
                "Kết quả duyệt hồ sơ phòng gym",
                "Hồ sơ phòng gym của bạn: " + decision
                        + (note != null && !note.isBlank() ? " — " + note : "") + ".",
                "/gym/profile",
                Map.of("decision", s(decision), "note", s(note)));
    }

    /**
     * Bug S2-01: Admin thấy địa chỉ gym không đúng chuẩn (thiếu quận/huyện, gõ tắt,
     * không geocode được) và yêu cầu gym xác minh lại. Gym vẫn hoạt động — đây là
     * lời nhắc, không phải đình chỉ.
     */
    public void gymAddressRecheckRequested(GymProfile profile, String note) {
        if (profile == null) return;
        dispatch("GYM_ADDRESS_RECHECK", profile.getUser(), NotificationCategory.ACCOUNT,
                "Cần xác minh lại địa chỉ phòng gym",
                "Địa chỉ \"" + s(profile.getAddress()) + "\" chưa đạt chuẩn"
                        + (note != null && !note.isBlank() ? ": " + note : "")
                        + ". Vui lòng cập nhật lại địa chỉ (chọn từ gợi ý bản đồ) để khách tìm đúng phòng gym.",
                "/gym/settings",
                Map.of("address", s(profile.getAddress()), "note", s(note)));
    }

    // ----- Review (UC-069/071) -----

    /** UC-071: đánh giá bị kiểm duyệt (ẩn/gỡ). */
    public void reviewModerated(Review r) {
        dispatch("REVIEW_MODERATED_CUSTOMER", r.getCustomer(), NotificationCategory.REVIEW,
                "Đánh giá của bạn được kiểm duyệt",
                "Đánh giá của bạn đã chuyển trạng thái " + r.getStatus() + ".",
                "/profile/reviews",
                Map.of("status", s(r.getStatus())));
    }

    // ==================================================================
    // Mô hình vé
    //
    // Gym KHÔNG còn nhận thông báo "có booking chờ duyệt" — gym không duyệt
    // lịch nữa (quyết định #7). Thứ gym cần biết chỉ là "có người đặt ngày nào".
    // ==================================================================

    private User gymUser(Ticket t) {
        GymProfile g = t.getGymProfile();
        return g != null ? g.getUser() : null;
    }

    /** Thanh toán vé thành công — khách yên tâm, gym biết có doanh thu mới. */
    public void ticketPaid(Ticket t) {
        Map<String, String> vars = Map.of(
                "ticketId", s(t.getId()), "ticketName", s(t.getTicketType().getName()),
                "amount", s(t.getPayableAmount()));
        dispatch("TICKET_PAID_CUSTOMER", t.getCustomer(), NotificationCategory.PAYMENT,
                "Vé đã kích hoạt",
                "Vé \"" + t.getTicketType().getName() + "\" đã sẵn sàng. Chọn ngày tập để bắt đầu.",
                "/tickets/" + t.getId(), vars);
        dispatch("TICKET_PAID_GYM", gymUser(t), NotificationCategory.PAYMENT,
                "Có vé mới được bán",
                "Khách vừa mua vé \"" + t.getTicketType().getName() + "\" tại "
                        + t.getGymBranch().getName() + ".",
                "/gym/tickets", vars);
    }

    /** Hết hạn cửa sổ thanh toán — vé bị huỷ, khách phải biết vì sao. */
    public void ticketPaymentExpired(Ticket t) {
        dispatch("TICKET_PAYMENT_EXPIRED", t.getCustomer(), NotificationCategory.PAYMENT,
                "Vé đã huỷ do quá hạn thanh toán",
                "Vé \"" + t.getTicketType().getName() + "\" bị huỷ vì chưa nhận được thanh toán. "
                        + "Điểm thưởng và lượt voucher (nếu có) đã được hoàn lại.",
                "/tickets", Map.of("ticketId", s(t.getId())));
    }

    /** Chuyển khoản không khớp (thiếu tiền / vào sau khi hết hạn) — khách phải biết. */
    public void ticketPaymentFailed(Ticket t, String reason) {
        dispatch("TICKET_PAYMENT_FAILED", t.getCustomer(), NotificationCategory.PAYMENT,
                "Thanh toán vé chưa thành công",
                "Vé \"" + t.getTicketType().getName() + "\" chưa được kích hoạt: " + reason + ".",
                "/tickets/" + t.getId(),
                Map.of("ticketId", s(t.getId()), "reason", s(reason)));
    }

    /** Khách đặt ngày tập — gym cần biết ai đến ngày nào; PT chỉ nhận khi được chọn. */
    public void sessionBooked(TrainingSession session) {
        Ticket t = session.getTicket();
        Map<String, String> vars = Map.of(
                "sessionId", s(session.getId()), "date", s(session.getSessionDate()),
                "customerName", s(t.getCustomer().getFullName()));
        dispatch("SESSION_BOOKED_GYM", gymUser(t), NotificationCategory.BOOKING,
                "Có lịch tập mới",
                t.getCustomer().getFullName() + " sẽ tập ngày " + session.getSessionDate()
                        + " tại " + t.getGymBranch().getName() + ".",
                "/gym/calendar", vars);
        if (session.getPtProfile() != null) {
            dispatch("SESSION_BOOKED_PT", session.getPtProfile().getUser(), NotificationCategory.BOOKING,
                    "Bạn có buổi dạy mới",
                    "Buổi " + session.getSessionDate() + " lúc " + session.getPtSlotStart()
                            + " với " + t.getCustomer().getFullName() + ".",
                    "/trainer/sessions", vars);
        }
    }

    /** Dời ngày (vé DAY) — gym và PT đang giữ chỗ ngày cũ phải được báo. */
    public void sessionRescheduled(TrainingSession session, java.time.LocalDate oldDate) {
        Ticket t = session.getTicket();
        String body = t.getCustomer().getFullName() + " dời buổi tập từ " + oldDate
                + " sang " + session.getSessionDate() + ".";
        Map<String, String> vars = Map.of(
                "sessionId", s(session.getId()), "oldDate", s(oldDate),
                "newDate", s(session.getSessionDate()));
        dispatch("SESSION_RESCHEDULED_GYM", gymUser(t), NotificationCategory.BOOKING,
                "Lịch tập được dời", body, "/gym/calendar", vars);
        if (session.getPtProfile() != null) {
            dispatch("SESSION_RESCHEDULED_PT", session.getPtProfile().getUser(),
                    NotificationCategory.BOOKING, "Buổi dạy được dời", body, "/trainer/sessions", vars);
        }
    }

    /** Câu 34: bổ sung/đổi PT cho một ngày — PT mới cần biết mình vừa có lịch. */
    public void sessionPtAssigned(TrainingSession session) {
        if (session.getPtProfile() == null) return;
        Ticket t = session.getTicket();
        dispatch("SESSION_PT_ASSIGNED", session.getPtProfile().getUser(), NotificationCategory.BOOKING,
                "Bạn được chọn cho một buổi tập",
                "Buổi " + session.getSessionDate() + " lúc " + session.getPtSlotStart()
                        + " với " + t.getCustomer().getFullName() + ".",
                "/trainer/sessions",
                Map.of("sessionId", s(session.getId()), "date", s(session.getSessionDate())));
    }

    /** Câu 31/33: gym xác nhận PT có đến kèm ảnh — khách thấy bằng chứng. */
    public void ptSessionConfirmed(TrainingSession session) {
        dispatch("SESSION_PT_CONFIRMED", session.getTicket().getCustomer(),
                NotificationCategory.BOOKING,
                "Phòng gym đã xác nhận buổi tập có PT",
                "Buổi " + session.getSessionDate() + " đã được phòng gym xác nhận kèm ảnh.",
                "/sessions/" + session.getId(),
                Map.of("sessionId", s(session.getId()), "date", s(session.getSessionDate())));
    }

    /** Tiền vé đã về ví khả dụng của gym (số ròng sau hoa hồng). */
    public void ticketSettlementReleased(Ticket t, java.math.BigDecimal netAmount) {
        dispatch("TICKET_SETTLEMENT_RELEASED", gymUser(t), NotificationCategory.PAYMENT,
                "Tiền đã về ví",
                "Vé #" + t.getId() + " đã giải ngân " + netAmount + " đ vào số dư khả dụng.",
                "/gym/wallet",
                Map.of("ticketId", s(t.getId()), "amount", s(netAmount)));
    }

    /** Vé sắp hết hạn mà khách chưa dùng hết — nhắc trước khi mất tiền (câu 32). */
    public void ticketExpiringSoon(Ticket t, long daysLeft) {
        dispatch("TICKET_EXPIRING_SOON", t.getCustomer(), NotificationCategory.BOOKING,
                "Vé sắp hết hạn",
                "Vé \"" + t.getTicketType().getName() + "\" còn " + daysLeft
                        + " ngày là hết hạn. Đặt lịch ngay để không mất quyền dùng.",
                "/tickets/" + t.getId(),
                Map.of("ticketId", s(t.getId()), "daysLeft", s(daysLeft)));
    }

    /** Vé đã hết hạn — tiền về gym, khách không hoàn được nữa (câu 32). */
    public void ticketExpired(Ticket t) {
        dispatch("TICKET_EXPIRED", t.getCustomer(), NotificationCategory.BOOKING,
                "Vé đã hết hạn",
                "Vé \"" + t.getTicketType().getName() + "\" đã quá hạn sử dụng ngày "
                        + t.getExpiresAt().toLocalDate() + " và không còn hoàn tiền được.",
                "/tickets/" + t.getId(), Map.of("ticketId", s(t.getId())));
    }

    /** Vé đã dùng hết — mở đánh giá phòng gym (câu 17). */
    public void ticketUsedUp(Ticket t) {
        dispatch("TICKET_USED_UP", t.getCustomer(), NotificationCategory.BOOKING,
                "Bạn đã dùng hết vé",
                "Vé \"" + t.getTicketType().getName() + "\" đã hoàn tất. "
                        + "Bạn có thể đánh giá phòng gym ngay bây giờ.",
                "/tickets/" + t.getId(), Map.of("ticketId", s(t.getId())));
    }

    /** Hoàn tiền đã thực thi — nói rõ luôn số buổi bị huỷ kèm (câu 12). */
    public void ticketRefundExecuted(Ticket t, java.math.BigDecimal amount, int cancelledSessions) {
        String body = "Đã hoàn " + amount + " đ vào ví của bạn cho vé \""
                + t.getTicketType().getName() + "\"."
                + (cancelledSessions > 0
                        ? " " + cancelledSessions + " buổi tập đã đặt bị huỷ theo." : "");
        dispatch("TICKET_REFUND_EXECUTED", t.getCustomer(), NotificationCategory.PAYMENT,
                "Yêu cầu hoàn tiền đã được duyệt", body, "/tickets/" + t.getId(),
                Map.of("ticketId", s(t.getId()), "amount", s(amount),
                        "cancelledSessions", s(cancelledSessions)));
    }

    /** Từ chối hoàn — khách phải biết và biết luôn đường đi tiếp. */
    public void ticketRefundRejected(Ticket t, java.math.BigDecimal amount, String note) {
        dispatch("TICKET_REFUND_REJECTED", t.getCustomer(), NotificationCategory.PAYMENT,
                "Yêu cầu hoàn tiền bị từ chối",
                "Yêu cầu hoàn " + amount + " đ cho vé \"" + t.getTicketType().getName()
                        + "\" đã bị từ chối: " + note
                        + ". Nếu chưa đồng ý, bạn có thể mở tranh chấp cho vé này.",
                "/tickets/" + t.getId(),
                Map.of("ticketId", s(t.getId()), "amount", s(amount), "note", s(note)));
    }

    /** Bên liên quan của một tranh chấp vé: khách, gym, và PT của buổi (nếu có). */
    private User[] ticketDisputeParties(Dispute d) {
        Ticket t = d.getTicket();
        User pt = d.getSession() != null && d.getSession().getPtProfile() != null
                ? d.getSession().getPtProfile().getUser() : null;
        return new User[]{t.getCustomer(), gymUser(t), pt};
    }

    private String ticketDisputeLinkFor(Dispute d, User recipient) {
        if (recipient == null) return "/notifications";
        User gym = gymUser(d.getTicket());
        if (gym != null && recipient.getUsername().equals(gym.getUsername())) return "/gym/disputes";
        User pt = d.getSession() != null && d.getSession().getPtProfile() != null
                ? d.getSession().getPtProfile().getUser() : null;
        if (pt != null && recipient.getUsername().equals(pt.getUsername())) return "/trainer/disputes";
        return "/profile/disputes";
    }

    public void disputeOpened(Dispute d, String openerUsername) {
        // Câu 34: nói rõ tranh chấp thuộc cấp vé hay cấp buổi — hai thứ này khác
        // nhau cả về số tiền lẫn về việc ai cần trả lời.
        String scope = d.getSession() != null
                ? "buổi tập ngày " + d.getSession().getSessionDate()
                : "vé #" + d.getTicket().getId();
        Map<String, String> vars = Map.of("disputeId", s(d.getId()),
                "ticketId", s(d.getTicket().getId()), "scope", scope);
        for (User u : ticketDisputeParties(d)) {
            if (u != null && !u.getUsername().equals(openerUsername)) {
                dispatch("DISPUTE_OPENED_PARTY", u, NotificationCategory.DISPUTE,
                        "Tranh chấp mới",
                        "Tranh chấp #" + d.getId() + " liên quan " + scope + " vừa được mở.",
                        ticketDisputeLinkFor(d, u), vars);
            }
        }
    }

    public void disputeResolved(Dispute d) {
        Map<String, String> vars = Map.of(
                "disputeId", s(d.getId()), "resolution", s(d.getResolution()));
        for (User u : ticketDisputeParties(d)) {
            dispatch("DISPUTE_RESOLVED_PARTY", u, NotificationCategory.DISPUTE,
                    "Tranh chấp đã được giải quyết",
                    "Tranh chấp #" + d.getId() + " kết luận: " + d.getResolution() + ".",
                    ticketDisputeLinkFor(d, u), vars);
        }
    }

    /**
     * PT bị Admin đình chỉ nhưng đã có khách chọn cho ngày cụ thể. Đình chỉ KHÔNG
     * bị chặn (đó là hành động an toàn), nên khách phải được báo để tự đổi PT —
     * nếu không thì một PT đang bị đình chỉ vẫn xuất hiện trong lịch của họ.
     */
    public void ptSuspendedAffectsSession(TrainingSession session, String reason) {
        Ticket t = session.getTicket();
        dispatch("PT_SUSPENDED_AFFECTS_SESSION", t.getCustomer(), NotificationCategory.BOOKING,
                "Buổi tập cần chọn lại PT",
                "PT của buổi ngày " + session.getSessionDate() + " tạm thời không thể phục vụ"
                        + (reason != null && !reason.isBlank() ? " (" + reason + ")" : "")
                        + ". Vui lòng chọn PT khác cho buổi này.",
                "/sessions/" + session.getId(),
                Map.of("sessionId", s(session.getId()), "date", s(session.getSessionDate()),
                        "reason", s(reason)));
    }

    /** Quyết định #8: PT khai dưới ngưỡng ngày — cảnh báo PT và gym quản lý. */
    public void ptAvailabilityBelowThreshold(User ptUser, User gymOwner, long daysDeclared, int threshold) {
        Map<String, String> vars = Map.of(
                "days", s(daysDeclared), "threshold", s(threshold));
        dispatch("PT_AVAILABILITY_LOW_PT", ptUser, NotificationCategory.SYSTEM,
                "Lịch rảnh của bạn còn ít",
                "Bạn mới khai " + daysDeclared + "/" + threshold
                        + " ngày. Khách vẫn đặt được, nhưng khai thêm sẽ có nhiều lịch hơn.",
                "/trainer/availability", vars);
        dispatch("PT_AVAILABILITY_LOW_GYM", gymOwner, NotificationCategory.SYSTEM,
                "PT khai lịch rảnh dưới ngưỡng",
                "Một PT của bạn mới khai " + daysDeclared + "/" + threshold + " ngày.",
                "/gym/pts", vars);
    }
}
