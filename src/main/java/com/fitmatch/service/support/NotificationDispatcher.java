package com.fitmatch.service.support;

import com.fitmatch.common.enums.NotificationCategory;
import com.fitmatch.entity.Booking;
import com.fitmatch.entity.Dispute;
import com.fitmatch.entity.GymProfile;
import com.fitmatch.entity.Review;
import com.fitmatch.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Sinh thông báo chuẩn hoá theo sự kiện (UC-075) và giải quyết người nhận từ
 * booking/dispute/review. Publish {@link NotificationEvent} để gửi SAU KHI giao
 * dịch nghiệp vụ commit (tránh "thông báo ma" khi rollback) — xem
 * {@link NotificationEventListener}. Mọi lỗi gửi được nuốt ở tầng NotificationService.
 */
@Component
@RequiredArgsConstructor
public class NotificationDispatcher {

    private final ApplicationEventPublisher eventPublisher;

    /**
     * Đưa yêu cầu gửi vào hàng đợi AFTER_COMMIT. Lấy username NGAY (trong giao
     * dịch, session còn mở -> lazy-safe) rồi mang theo trong event.
     */
    private void dispatch(User user, NotificationCategory category, String title, String body, String link) {
        if (user == null) return;
        eventPublisher.publishEvent(new NotificationEvent(user.getUsername(), category, title, body, link));
    }

    private User gymUser(Booking b) {
        GymProfile g = b.getGymProfile();
        return g != null ? g.getUser() : null;
    }

    private User ptUser(Booking b) {
        return b.getPtProfile() != null ? b.getPtProfile().getUser() : null;
    }

    // ----- E-15/BE-14 (audit 2026-07-17): các flow trước đây thiếu thông báo -----

    /** UC-042: khách tự hủy — gym (và PT nếu đã gán) phải biết lịch trống ra. */
    public void bookingCancelledByCustomer(Booking b, String reason) {
        String body = "Khách đã hủy booking #" + b.getId()
                + (reason != null && !reason.isBlank() ? ": " + reason : ".");
        dispatch(gymUser(b), NotificationCategory.BOOKING, "Khách hủy lịch đặt", body, "/gym/bookings");
        dispatch(ptUser(b), NotificationCategory.BOOKING, "Buổi tập bị khách hủy", body, "/trainer/bookings");
    }

    /** UC-062: kết quả xử lý yêu cầu rút tiền — gym không còn phải tự vào kiểm tra. */
    public void withdrawalDecided(User gymUser, Long withdrawalId, String decision, String detail) {
        dispatch(gymUser, NotificationCategory.PAYMENT,
                "Yêu cầu rút tiền #" + withdrawalId + " " + decision,
                detail,
                "/gym/withdrawals");
    }

    /** UC-044: slot trống ra (booking hủy/từ chối) — báo khách đang chờ cùng dịch vụ/gói. */
    public void waitlistSlotOpened(User customer, String itemName) {
        dispatch(customer, NotificationCategory.BOOKING,
                "Đã có chỗ trống",
                "Khung giờ cho \"" + itemName + "\" vừa trống ra — đặt ngay trước khi hết chỗ.",
                "/profile/bookings");
    }

    // ----- Booking (UC-037/038/040/049) -----

    /** UC-037: booking được chuyển tới Gym xử lý. */
    public void bookingRoutedToGym(Booking b) {
        dispatch(gymUser(b), NotificationCategory.BOOKING,
                "Yêu cầu đặt lịch mới",
                "Booking #" + b.getId() + " đang chờ bạn xác nhận.",
                "/gym/bookings");
    }

    /** UC-038: Gym nhận booking. */
    public void bookingAccepted(Booking b) {
        dispatch(b.getCustomer(), NotificationCategory.BOOKING,
                "Lịch đặt đã được xác nhận",
                "Booking #" + b.getId() + " đã được phòng gym xác nhận.",
                "/profile/bookings");
        dispatch(ptUser(b), NotificationCategory.BOOKING,
                "Bạn được phân công buổi tập",
                "Booking #" + b.getId() + " đã được gán cho bạn.",
                "/trainer/bookings");
    }

    /** UC-038: Gym từ chối booking. */
    public void bookingRejected(Booking b, String reason) {
        dispatch(b.getCustomer(), NotificationCategory.BOOKING,
                "Lịch đặt bị từ chối",
                "Booking #" + b.getId() + " bị từ chối: " + reason
                        + ". Tiền (nếu có) sẽ được hoàn.",
                "/profile/bookings");
    }

    /** UC-042: Gym hủy booking. */
    public void bookingCancelledByGym(Booking b, String reason) {
        dispatch(b.getCustomer(), NotificationCategory.BOOKING,
                "Lịch đặt bị hủy",
                "Booking #" + b.getId() + " bị phòng gym hủy: " + reason + ".",
                "/profile/bookings");
    }

    /** UC-049: buổi tập hoàn tất. */
    public void bookingCompleted(Booking b) {
        dispatch(b.getCustomer(), NotificationCategory.BOOKING,
                "Buổi tập hoàn tất",
                "Booking #" + b.getId() + " đã hoàn tất. Bạn có thể đánh giá buổi tập.",
                "/profile/reviews");
    }

    /** UC-043: đánh dấu vắng mặt. */
    public void bookingNoShow(Booking b) {
        dispatch(b.getCustomer(), NotificationCategory.BOOKING,
                "Ghi nhận vắng mặt",
                "Booking #" + b.getId() + " được ghi nhận là vắng mặt.",
                "/profile/bookings");
    }

    /**
     * P1-16 (UC-021): PT bị đình chỉ nhưng còn booking tương lai — báo cho Gym
     * Operator (chịu trách nhiệm reassign/hủy) và khách hàng của buổi bị ảnh hưởng.
     */
    public void ptSuspendedAffectsBooking(Booking b, String reason) {
        dispatch(gymUser(b), NotificationCategory.BOOKING,
                "PT bị đình chỉ — cần xử lý booking",
                "PT của booking #" + b.getId() + " đã bị đình chỉ"
                        + (reason != null && !reason.isBlank() ? ": " + reason : "")
                        + ". Vui lòng phân công PT khác hoặc hủy buổi này.",
                "/gym/bookings");
        dispatch(b.getCustomer(), NotificationCategory.BOOKING,
                "Buổi tập cần được sắp xếp lại",
                "PT của booking #" + b.getId() + " tạm thời không thể phục vụ. "
                        + "Phòng gym sẽ liên hệ để sắp xếp lại.",
                "/profile/bookings");
    }

    // ----- Gym verification (UC-013/014) -----

    /** UC-013/014: thông báo cho Gym Operator kết quả duyệt/đình chỉ hồ sơ. */
    public void gymVerificationDecided(GymProfile profile, String decision, String note) {
        if (profile == null) return;
        dispatch(profile.getUser(), NotificationCategory.ACCOUNT,
                "Kết quả duyệt hồ sơ phòng gym",
                "Hồ sơ phòng gym của bạn: " + decision
                        + (note != null && !note.isBlank() ? " — " + note : "") + ".",
                "/gym/profile");
    }

    // ----- Payment / Settlement (UC-053/056/059) -----

    /** UC-036/053: tiền đã được giữ cho booking. */
    public void paymentHeld(Booking b) {
        dispatch(b.getCustomer(), NotificationCategory.PAYMENT,
                "Thanh toán thành công",
                "Đã nhận thanh toán cho booking #" + b.getId() + ".",
                "/profile/payments");
    }

    /** Bug 9 (UC-054): đơn VietQR hết hạn — booking bị hủy, báo khách rõ ràng. */
    public void paymentExpired(Booking b) {
        dispatch(b.getCustomer(), NotificationCategory.PAYMENT,
                "Hết hạn thanh toán",
                "Booking #" + b.getId() + " đã bị hủy vì quá hạn thanh toán. "
                        + "Điểm/voucher đã dùng (nếu có) được hoàn lại — bạn có thể đặt lịch mới.",
                "/profile/bookings");
    }

    /** Bug 9 (UC-053): thanh toán thất bại/không khớp (thiếu tiền, chuyển sau khi hết hạn...). */
    public void paymentFailed(Booking b, String reason) {
        dispatch(b.getCustomer(), NotificationCategory.PAYMENT,
                "Thanh toán thất bại",
                "Thanh toán cho booking #" + b.getId() + " chưa được ghi nhận: " + reason
                        + ". Vui lòng liên hệ hỗ trợ nếu bạn đã chuyển khoản.",
                "/profile/payments");
    }

    /** UC-056/067: đã hoàn tiền cho khách. */
    public void refundExecuted(Booking b, BigDecimal amount) {
        dispatch(b.getCustomer(), NotificationCategory.PAYMENT,
                "Hoàn tiền đã thực hiện",
                "Đã hoàn " + amount + " cho booking #" + b.getId() + ".",
                "/profile/payments");
    }

    /** UC-059: tiền đã giải ngân về ví Gym. */
    public void settlementReleased(Booking b, BigDecimal net) {
        dispatch(gymUser(b), NotificationCategory.SETTLEMENT,
                "Tiền đã về ví khả dụng",
                "Booking #" + b.getId() + ": " + net + " đã sẵn sàng để rút.",
                "/gym/withdrawals");
    }

    // ----- Dispute (UC-063/066) -----

    /** UC-063: tranh chấp được mở — báo cho các bên còn lại. */
    public void disputeOpened(Dispute d, String openerUsername) {
        Booking b = d.getBooking();
        for (User u : new User[]{b.getCustomer(), gymUser(b), ptUser(b)}) {
            if (u != null && !u.getUsername().equals(openerUsername)) {
                dispatch(u, NotificationCategory.DISPUTE,
                        "Tranh chấp mới",
                        "Tranh chấp #" + d.getId() + " liên quan booking #" + b.getId()
                                + " vừa được mở.",
                        "/notifications");
            }
        }
    }

    /** UC-066: tranh chấp đã giải quyết — báo mọi bên. */
    public void disputeResolved(Dispute d) {
        Booking b = d.getBooking();
        for (User u : new User[]{b.getCustomer(), gymUser(b), ptUser(b)}) {
            dispatch(u, NotificationCategory.DISPUTE,
                    "Tranh chấp đã được giải quyết",
                    "Tranh chấp #" + d.getId() + " kết luận: " + d.getResolution() + ".",
                    "/notifications");
        }
    }

    // ----- Review (UC-069/071) -----

    /** UC-069: Gym phản hồi đánh giá của khách. */
    public void reviewReplied(Review r) {
        dispatch(r.getCustomer(), NotificationCategory.REVIEW,
                "Phòng gym đã phản hồi đánh giá",
                "Đánh giá của bạn cho " + r.getGymProfile().getGymName() + " đã được phản hồi.",
                "/profile/reviews");
    }

    /** UC-071: đánh giá bị kiểm duyệt (ẩn/gỡ). */
    public void reviewModerated(Review r) {
        dispatch(r.getCustomer(), NotificationCategory.REVIEW,
                "Đánh giá của bạn được kiểm duyệt",
                "Đánh giá của bạn đã chuyển trạng thái " + r.getStatus() + ".",
                "/profile/reviews");
    }
}
