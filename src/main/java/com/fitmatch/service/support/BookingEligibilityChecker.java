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
            reasons.add("Exactly one of service or package must be selected");
        }

        // Gym phải APPROVED + đang hiển thị.
        if (booking.getGymProfile().getVerificationStatus() != VerificationStatus.APPROVED
                || !booking.getGymProfile().isActive()) {
            reasons.add("Gym is not available for booking");
        }

        // Catalog phải PUBLISHED — trừ buổi tập từ gói đã mua (khách vẫn còn quyền
        // dùng buổi kể cả khi gym đã ẩn/ngừng bán gói).
        boolean packageSession = booking.getCustomerPackage() != null;
        if (hasService && booking.getGymService().getStatus() != CatalogStatus.PUBLISHED) {
            reasons.add("Service is not published");
        }
        if (hasPackage && !packageSession
                && booking.getTrainingPackage().getStatus() != CatalogStatus.PUBLISHED) {
            reasons.add("Package is not published");
        }

        // UC-049: gói đã mua phải còn hiệu lực và còn buổi (kể cả buổi đang giữ chỗ).
        if (packageSession) {
            var cp = booking.getCustomerPackage();
            if (cp.getStatus() != com.fitmatch.common.enums.CustomerPackageStatus.ACTIVE) {
                reasons.add("Customer package is " + cp.getStatus());
            } else {
                long holding = bookingRepository.countByCustomerPackage_IdAndStatusInAndIdNot(
                        cp.getId(), HOLDING_STATUSES, booking.getId());
                if (cp.getSessionsUsed() + holding >= cp.getSessionsTotal()) {
                    reasons.add("No remaining sessions in the purchased package (used "
                            + cp.getSessionsUsed() + ", holding " + holding
                            + " of " + cp.getSessionsTotal() + ")");
                }
            }
        }

        // Khung giờ bắt buộc và ở tương lai.
        LocalDateTime start = booking.getStartAt();
        LocalDateTime end = booking.getEndAt();
        if (start == null || end == null) {
            reasons.add("startAt and endAt are required");
        } else {
            if (!start.isAfter(LocalDateTime.now())) {
                reasons.add("startAt must be in the future");
            }
            // Notice tối thiểu theo booking rules (UC-026).
            BookingRules rules = hasService && booking.getGymService() != null
                    ? booking.getGymService().getBookingRules()
                    : (hasPackage && booking.getTrainingPackage() != null
                            ? booking.getTrainingPackage().getBookingRules() : null);
            if (rules != null && rules.getMinNoticeHours() != null) {
                long noticeHours = Duration.between(LocalDateTime.now(), start).toHours();
                if (noticeHours < rules.getMinNoticeHours()) {
                    reasons.add("Booking requires at least " + rules.getMinNoticeHours() + " hours notice");
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
                    reasons.add("Branch capacity is full for this time slot");
                }
            }
        }

        if (!reasons.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Booking is not eligible: " + String.join("; ", reasons));
        }
    }

    /**
     * UC-041: các vi phạm khi dời booking sang khung giờ mới — PT (nếu có) và
     * chi nhánh (nếu có) phải nhận được [start, end), loại trừ chính booking này.
     */
    public List<String> rescheduleIssues(Booking booking, LocalDateTime start, LocalDateTime end) {
        List<String> reasons = new ArrayList<>();
        if (!start.isAfter(LocalDateTime.now())) {
            reasons.add("startAt must be in the future");
        }
        // UC-041: dời lịch vẫn phải tôn trọng trạng thái Gym và notice tối thiểu.
        if (booking.getGymProfile().getVerificationStatus() != VerificationStatus.APPROVED
                || !booking.getGymProfile().isActive()) {
            reasons.add("Gym is not available for booking");
        }
        BookingRules rules = booking.getGymService() != null
                ? booking.getGymService().getBookingRules()
                : (booking.getTrainingPackage() != null
                        ? booking.getTrainingPackage().getBookingRules() : null);
        if (rules != null && rules.getMinNoticeHours() != null
                && Duration.between(LocalDateTime.now(), start).toHours() < rules.getMinNoticeHours()) {
            reasons.add("Booking requires at least " + rules.getMinNoticeHours() + " hours notice");
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
                reasons.add("PT already has a booking in this time slot");
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
                    reasons.add("Branch capacity is full for this time slot");
                }
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
            reasons.add("PT is not assigned to the selected service/package/branch");
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
                reasons.add("PT already has a booking in this time slot");
            }
        }
        return reasons;
    }
}
