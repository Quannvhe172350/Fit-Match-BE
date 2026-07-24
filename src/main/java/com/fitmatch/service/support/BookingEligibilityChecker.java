package com.fitmatch.service.support;

import com.fitmatch.common.enums.BookingStatus;
import com.fitmatch.common.enums.CatalogStatus;
import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.VerificationStatus;
import com.fitmatch.entity.Booking;
import com.fitmatch.entity.BookingRules;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.repository.BookingRepository;
import com.fitmatch.repository.PtAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * UC-033: kiểm tra đủ điều kiện trước checkout — trạng thái Gym/catalog,
 * khung giờ + notice tối thiểu, PT được gán đúng đích, xung đột lịch (UC-030),
 * double-booking và capacity chi nhánh.
 */
@Component
@RequiredArgsConstructor
public class BookingEligibilityChecker {

    /** Trạng thái đang giữ chỗ — dùng cho check trùng lịch/capacity. */
    public static final Set<BookingStatus> HOLDING_STATUSES =
            Set.of(BookingStatus.PENDING_PAYMENT, BookingStatus.PENDING_GYM, BookingStatus.CONFIRMED);

    private final ScheduleConflictValidator scheduleConflictValidator;
    private final BookingRepository bookingRepository;
    private final PtAssignmentRepository ptAssignmentRepository;

    /** Ném BusinessException với danh sách vi phạm nếu booking chưa đủ điều kiện. */
    public void assertEligible(Booking booking) {
        List<String> reasons = new ArrayList<>();

        // Đúng một trong service/package.
        boolean hasService = booking.getGymService() != null;
        boolean hasPackage = booking.getTrainingPackage() != null;
        if (hasService == hasPackage) {
            reasons.add("Phải chọn đúng một trong hai: dịch vụ hoặc gói tập");
        }

        // Gym phải APPROVED + đang hiển thị.
        if (booking.getGymProfile().getVerificationStatus() != VerificationStatus.APPROVED
                || !booking.getGymProfile().isActive()) {
            reasons.add("Phòng gym hiện không nhận đặt lịch");
        }

        // Catalog phải PUBLISHED — trừ buổi tập từ gói đã mua (khách vẫn còn quyền
        // dùng buổi kể cả khi gym đã ẩn/ngừng bán gói).
        boolean packageSession = booking.getCustomerPackage() != null;
        if (hasService && booking.getGymService().getStatus() != CatalogStatus.PUBLISHED) {
            reasons.add("Dịch vụ này hiện chưa được mở bán");
        }
        if (hasPackage && !packageSession
                && booking.getTrainingPackage().getStatus() != CatalogStatus.PUBLISHED) {
            reasons.add("Gói tập này hiện chưa được mở bán");
        }

        // UC-049: gói đã mua phải còn hiệu lực và còn buổi (kể cả buổi đang giữ chỗ).
        if (packageSession) {
            var cp = booking.getCustomerPackage();
            if (cp.getStatus() != com.fitmatch.common.enums.CustomerPackageStatus.ACTIVE) {
                reasons.add("Gói đã mua không còn hiệu lực (trạng thái: " + cp.getStatus() + ")");
            } else {
                long holding = bookingRepository.countByCustomerPackage_IdAndStatusInAndIdNot(
                        cp.getId(), HOLDING_STATUSES, booking.getId());
                if (cp.getSessionsUsed() + holding >= cp.getSessionsTotal()) {
                    reasons.add("Gói đã mua không còn buổi trống (đã dùng " + cp.getSessionsUsed()
                            + ", đang giữ chỗ " + holding + " trên tổng " + cp.getSessionsTotal() + " buổi)");
                }
            }
        }

        // Khung giờ bắt buộc và ở tương lai.
        LocalDateTime start = booking.getStartAt();
        LocalDateTime end = booking.getEndAt();
        if (start == null || end == null) {
            reasons.add("Vui lòng chọn thời gian bắt đầu và kết thúc");
        } else {
            if (!start.isAfter(LocalDateTime.now())) {
                reasons.add("Thời gian bắt đầu phải ở tương lai");
            }
            // P2-2.10: chốt end > start ngay ở enforcement — booking chỉ có dịch vụ (không
            // PT, không chi nhánh) không đi qua checkPt/checkBranch nên nếu không kiểm ở đây
            // sẽ chấp nhận khung giờ âm/0.
            if (!start.isBefore(end)) {
                reasons.add("Giờ kết thúc phải sau giờ bắt đầu");
            }
            // Notice tối thiểu theo booking rules (UC-026).
            BookingRules rules = hasService && booking.getGymService() != null
                    ? booking.getGymService().getBookingRules()
                    : (hasPackage && booking.getTrainingPackage() != null
                            ? booking.getTrainingPackage().getBookingRules() : null);
            if (rules != null && rules.getMinNoticeHours() != null) {
                long noticeHours = Duration.between(LocalDateTime.now(), start).toHours();
                if (noticeHours < rules.getMinNoticeHours()) {
                    reasons.add("Lịch đặt cần báo trước ít nhất " + rules.getMinNoticeHours() + " giờ");
                }
            }
        }

        // PT: phải được gán vào đích đã chọn + rảnh + không trùng booking khác.
        if (booking.getPtProfile() != null) {
            reasons.addAll(ptIssues(booking, booking.getPtProfile().getId()));
        }

        // Chi nhánh: mở cửa + không bị chặn + còn capacity.
        if (booking.getGymBranch() != null && start != null && end != null) {
            reasons.addAll(scheduleConflictValidator.checkBranch(booking.getGymBranch().getId(), start, end));
            Integer capacity = booking.getGymBranch().getCapacity();
            if (capacity != null) {
                long held = bookingRepository
                        .countByGymBranch_IdAndStatusInAndStartAtLessThanAndEndAtGreaterThan(
                                booking.getGymBranch().getId(), HOLDING_STATUSES, end, start);
                if (held >= capacity) {
                    reasons.add("Khung giờ này đã kín chỗ tại chi nhánh");
                }
            }
        }

        // UC-030 (phía khách): một khách chỉ được giữ 1 booking cho 1 khung giờ.
        if (start != null && end != null && booking.getCustomer() != null) {
            long overlapping = bookingRepository
                    .countByCustomer_IdAndStatusInAndStartAtLessThanAndEndAtGreaterThanAndIdNot(
                            booking.getCustomer().getId(), HOLDING_STATUSES, end, start, booking.getId());
            if (overlapping > 0) {
                reasons.add("Bạn đã có lịch đặt khác trùng khung giờ này");
            }
        }

        if (!reasons.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Không thể đặt lịch: " + String.join("; ", reasons));
        }
    }

    /**
     * UC-041: các vi phạm khi dời booking sang khung giờ mới — PT (nếu có) và
     * chi nhánh (nếu có) phải nhận được [start, end), loại trừ chính booking này.
     */
    public List<String> rescheduleIssues(Booking booking, LocalDateTime start, LocalDateTime end) {
        List<String> reasons = new ArrayList<>();
        if (!start.isAfter(LocalDateTime.now())) {
            reasons.add("Thời gian bắt đầu phải ở tương lai");
        }
        // P2-2.10: end > start (booking chỉ có dịch vụ không đi qua checkPt/checkBranch).
        if (!start.isBefore(end)) {
            reasons.add("Giờ kết thúc phải sau giờ bắt đầu");
        }
        // UC-041: dời lịch vẫn phải tôn trọng trạng thái Gym và notice tối thiểu.
        if (booking.getGymProfile().getVerificationStatus() != VerificationStatus.APPROVED
                || !booking.getGymProfile().isActive()) {
            reasons.add("Phòng gym hiện không nhận đặt lịch");
        }
        BookingRules rules = booking.getGymService() != null
                ? booking.getGymService().getBookingRules()
                : (booking.getTrainingPackage() != null
                        ? booking.getTrainingPackage().getBookingRules() : null);
        if (rules != null && rules.getMinNoticeHours() != null
                && Duration.between(LocalDateTime.now(), start).toHours() < rules.getMinNoticeHours()) {
            reasons.add("Lịch đặt cần báo trước ít nhất " + rules.getMinNoticeHours() + " giờ");
        }
        if (booking.getPtProfile() != null) {
            Long ptId = booking.getPtProfile().getId();
            reasons.addAll(scheduleConflictValidator.checkPt(ptId, start, end));
            boolean overlapping = !bookingRepository
                    .findByPtProfile_IdAndStatusInAndStartAtLessThanAndEndAtGreaterThan(
                            ptId, HOLDING_STATUSES, end, start).stream()
                    .filter(b -> !b.getId().equals(booking.getId()))
                    .toList().isEmpty();
            if (overlapping) {
                reasons.add("PT đã có lịch đặt trong khung giờ này");
            }
        }
        if (booking.getGymBranch() != null) {
            reasons.addAll(scheduleConflictValidator.checkBranch(booking.getGymBranch().getId(), start, end));
            Integer capacity = booking.getGymBranch().getCapacity();
            if (capacity != null) {
                // Loại trừ chính booking đang dời — khung giờ cũ có thể trùng khung mới.
                long held = bookingRepository
                        .countByGymBranch_IdAndStatusInAndStartAtLessThanAndEndAtGreaterThanAndIdNot(
                                booking.getGymBranch().getId(), HOLDING_STATUSES, end, start, booking.getId());
                if (held >= capacity) {
                    reasons.add("Khung giờ này đã kín chỗ tại chi nhánh");
                }
            }
        }
        // UC-030 (phía khách): khung giờ mới không được trùng lịch đặt khác của chính khách.
        if (booking.getCustomer() != null) {
            long overlapping = bookingRepository
                    .countByCustomer_IdAndStatusInAndStartAtLessThanAndEndAtGreaterThanAndIdNot(
                            booking.getCustomer().getId(), HOLDING_STATUSES, end, start, booking.getId());
            if (overlapping > 0) {
                reasons.add("Bạn đã có lịch đặt khác trùng khung giờ này");
            }
        }
        return reasons;
    }

    /**
     * UC-039: các vi phạm khi gán một PT cho booking — assignment với đích đã chọn,
     * xung đột lịch (UC-030) và trùng booking đang giữ chỗ khác.
     */
    public List<String> ptIssues(Booking booking, Long ptId) {
        List<String> reasons = new ArrayList<>();
        boolean assigned =
                (booking.getGymService() != null
                        && ptAssignmentRepository.existsByPtProfile_IdAndGymService_Id(
                                ptId, booking.getGymService().getId()))
                || (booking.getTrainingPackage() != null
                        && ptAssignmentRepository.existsByPtProfile_IdAndTrainingPackage_Id(
                                ptId, booking.getTrainingPackage().getId()))
                || (booking.getGymBranch() != null
                        && ptAssignmentRepository.existsByPtProfile_IdAndGymBranch_Id(
                                ptId, booking.getGymBranch().getId()));
        if (!assigned) {
            reasons.add("PT chưa được gán cho dịch vụ/gói/chi nhánh đã chọn");
        }
        LocalDateTime start = booking.getStartAt();
        LocalDateTime end = booking.getEndAt();
        if (start != null && end != null) {
            reasons.addAll(scheduleConflictValidator.checkPt(ptId, start, end));
            boolean overlapping = !bookingRepository
                    .findByPtProfile_IdAndStatusInAndStartAtLessThanAndEndAtGreaterThan(
                            ptId, HOLDING_STATUSES, end, start).stream()
                    .filter(b -> !b.getId().equals(booking.getId()))
                    .toList().isEmpty();
            if (overlapping) {
                reasons.add("PT đã có lịch đặt trong khung giờ này");
            }
        }
        return reasons;
    }
}
