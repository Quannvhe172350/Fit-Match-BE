package com.fitmatch.service.support;

import com.fitmatch.common.enums.NotificationCategory;
import com.fitmatch.entity.Booking;
import com.fitmatch.entity.Dispute;
import com.fitmatch.entity.GymProfile;
import com.fitmatch.entity.Review;
import com.fitmatch.entity.User;
import com.fitmatch.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Sinh thông báo chuẩn hoá theo sự kiện (UC-075) và giải quyết người nhận từ
 * booking/dispute/review. Mọi lỗi được nuốt ở tầng NotificationService.
 */
@Component
@RequiredArgsConstructor
public class NotificationDispatcher {

    private final NotificationService notificationService;

    private User gymUser(Booking b) {
        GymProfile g = b.getGymProfile();
        return g != null ? g.getUser() : null;
    }

    private User ptUser(Booking b) {
        return b.getPtProfile() != null ? b.getPtProfile().getUser() : null;
    }

    // ----- Booking (UC-037/038/040/049) -----

    /** UC-037: booking được chuyển tới Gym xử lý. */
    public void bookingRoutedToGym(Booking b) {
        notificationService.notify(gymUser(b), NotificationCategory.BOOKING,
                "Yêu cầu đặt lịch mới",
                "Booking #" + b.getId() + " đang chờ bạn xác nhận.",
                "/gym/bookings");
    }

    /** UC-038: Gym nhận booking. */
    public void bookingAccepted(Booking b) {
        notificationService.notify(b.getCustomer(), NotificationCategory.BOOKING,
                "Lịch đặt đã được xác nhận",
                "Booking #" + b.getId() + " đã được phòng gym xác nhận.",
                "/profile/bookings");
        notificationService.notify(ptUser(b), NotificationCategory.BOOKING,
                "Bạn được phân công buổi tập",
                "Booking #" + b.getId() + " đã được gán cho bạn.",
                "/trainer/bookings");
    }

    /** UC-038: Gym từ chối booking. */
    public void bookingRejected(Booking b, String reason) {
        notificationService.notify(b.getCustomer(), NotificationCategory.BOOKING,
                "Lịch đặt bị từ chối",
                "Booking #" + b.getId() + " bị từ chối: " + reason
                        + ". Tiền (nếu có) sẽ được hoàn.",
                "/profile/bookings");
    }

    /** UC-042: Gym hủy booking. */
    public void bookingCancelledByGym(Booking b, String reason) {
        notificationService.notify(b.getCustomer(), NotificationCategory.BOOKING,
                "Lịch đặt bị hủy",
                "Booking #" + b.getId() + " bị phòng gym hủy: " + reason + ".",
                "/profile/bookings");
    }

    /** UC-049: buổi tập hoàn tất. */
    public void bookingCompleted(Booking b) {
        notificationService.notify(b.getCustomer(), NotificationCategory.BOOKING,
                "Buổi tập hoàn tất",
                "Booking #" + b.getId() + " đã hoàn tất. Bạn có thể đánh giá buổi tập.",
                "/profile/reviews");
    }

    /** UC-043: đánh dấu vắng mặt. */
    public void bookingNoShow(Booking b) {
        notificationService.notify(b.getCustomer(), NotificationCategory.BOOKING,
                "Ghi nhận vắng mặt",
                "Booking #" + b.getId() + " được ghi nhận là vắng mặt.",
                "/profile/bookings");
    }

    // ----- Payment / Settlement (UC-053/056/059) -----

    /** UC-036/053: tiền đã được giữ cho booking. */
    public void paymentHeld(Booking b) {
        notificationService.notify(b.getCustomer(), NotificationCategory.PAYMENT,
                "Thanh toán thành công",
                "Đã nhận thanh toán cho booking #" + b.getId() + ".",
                "/profile/payments");
    }

    /** UC-056/067: đã hoàn tiền cho khách. */
    public void refundExecuted(Booking b, BigDecimal amount) {
        notificationService.notify(b.getCustomer(), NotificationCategory.PAYMENT,
                "Hoàn tiền đã thực hiện",
                "Đã hoàn " + amount + " cho booking #" + b.getId() + ".",
                "/profile/payments");
    }

    /** UC-059: tiền đã giải ngân về ví Gym. */
    public void settlementReleased(Booking b, BigDecimal net) {
        notificationService.notify(gymUser(b), NotificationCategory.SETTLEMENT,
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
                notificationService.notify(u, NotificationCategory.DISPUTE,
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
            notificationService.notify(u, NotificationCategory.DISPUTE,
                    "Tranh chấp đã được giải quyết",
                    "Tranh chấp #" + d.getId() + " kết luận: " + d.getResolution() + ".",
                    "/notifications");
        }
    }

    // ----- Review (UC-069/071) -----

    /** UC-069: Gym phản hồi đánh giá của khách. */
    public void reviewReplied(Review r) {
        notificationService.notify(r.getCustomer(), NotificationCategory.REVIEW,
                "Phòng gym đã phản hồi đánh giá",
                "Đánh giá của bạn cho " + r.getGymProfile().getGymName() + " đã được phản hồi.",
                "/profile/reviews");
    }

    /** UC-071: đánh giá bị kiểm duyệt (ẩn/gỡ). */
    public void reviewModerated(Review r) {
        notificationService.notify(r.getCustomer(), NotificationCategory.REVIEW,
                "Đánh giá của bạn được kiểm duyệt",
                "Đánh giá của bạn đã chuyển trạng thái " + r.getStatus() + ".",
                "/profile/reviews");
    }
}
