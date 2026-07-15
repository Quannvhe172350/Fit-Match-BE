package com.fitmatch.service.impl;

import com.fitmatch.common.enums.BookingStatus;
import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.dto.booking.BookingResponse;
import com.fitmatch.dto.booking.CreateBookingRequest;
import com.fitmatch.entity.Booking;
import com.fitmatch.entity.GymBranch;
import com.fitmatch.entity.GymProfile;
import com.fitmatch.entity.GymService;
import com.fitmatch.entity.PtProfile;
import com.fitmatch.entity.TrainingPackage;
import com.fitmatch.entity.User;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.BookingRepository;
import com.fitmatch.repository.GymBranchRepository;
import com.fitmatch.repository.GymServiceRepository;
import com.fitmatch.repository.PtProfileRepository;
import com.fitmatch.repository.TrainingPackageRepository;
import com.fitmatch.repository.UserRepository;
import com.fitmatch.service.BookingService;
import com.fitmatch.service.PaymentService;
import com.fitmatch.service.RefundService;
import com.fitmatch.service.support.BookingEligibilityChecker;
import com.fitmatch.service.support.BookingLifecycle;
import com.fitmatch.service.support.BookingPriceCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final GymServiceRepository gymServiceRepository;
    private final TrainingPackageRepository trainingPackageRepository;
    private final GymBranchRepository gymBranchRepository;
    private final PtProfileRepository ptProfileRepository;
    private final BookingEligibilityChecker bookingEligibilityChecker;
    private final BookingPriceCalculator bookingPriceCalculator;
    private final BookingLifecycle bookingLifecycle;
    private final PaymentService paymentService;
    private final RefundService refundService;
    private final com.fitmatch.service.PackageUsageService packageUsageService;
    private final com.fitmatch.service.support.AttendanceSupport attendanceSupport;
    private final com.fitmatch.service.VoucherService voucherService;

    @Override
    @Transactional
    public BookingResponse createDraft(String customerUsername, CreateBookingRequest request) {
        User customer = userRepository.findByUsername(customerUsername)
                .orElseThrow(() -> new ResourceNotFoundException("User", customerUsername));

        Selection selection = resolveSelection(request, null, customerUsername);

        Booking booking = bookingRepository.save(Booking.builder()
                .customer(customer)
                .gymProfile(selection.gym)
                .gymService(selection.service)
                .trainingPackage(selection.trainingPackage)
                .customerPackage(selection.customerPackage)
                .gymBranch(selection.branch)
                .ptProfile(selection.pt)
                .startAt(request.getStartAt())
                .endAt(request.getEndAt())
                .customerNote(request.getNote())
                .status(BookingStatus.DRAFT)
                .build());
        log.info("Customer {} created draft booking {} for gym {}",
                customerUsername, booking.getId(), selection.gym.getId());
        return BookingResponse.of(booking);
    }

    @Override
    @Transactional
    public BookingResponse updateSelection(String customerUsername, Long bookingId, CreateBookingRequest request) {
        Booking booking = bookingRepository.findByIdAndCustomer_Username(bookingId, customerUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
        if (booking.getStatus() != BookingStatus.DRAFT) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Selection can only be changed while the booking is DRAFT (current: " + booking.getStatus() + ")");
        }

        Selection selection = resolveSelection(request, booking.getGymProfile(), customerUsername);
        booking.setGymProfile(selection.gym);
        if (request.getServiceId() != null) {
            booking.setGymService(selection.service);
        }
        if (request.getPackageId() != null) {
            booking.setTrainingPackage(selection.trainingPackage);
        }
        if (request.getCustomerPackageId() != null) {
            booking.setTrainingPackage(selection.trainingPackage);
            booking.setCustomerPackage(selection.customerPackage);
        }
        if (request.getBranchId() != null) {
            booking.setGymBranch(selection.branch);
        }
        if (request.getPtId() != null) {
            booking.setPtProfile(selection.pt);
        }
        if (request.getStartAt() != null) {
            booking.setStartAt(request.getStartAt());
        }
        if (request.getEndAt() != null) {
            booking.setEndAt(request.getEndAt());
        }
        if (request.getNote() != null) {
            booking.setCustomerNote(request.getNote());
        }
        bookingRepository.save(booking);
        log.info("Customer {} updated selection of booking {}", customerUsername, bookingId);
        return BookingResponse.of(booking);
    }

    @Override
    @Transactional
    public BookingResponse checkout(String customerUsername, Long bookingId) {
        Booking booking = bookingRepository.findByIdAndCustomer_Username(bookingId, customerUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
        if (booking.getStatus() != BookingStatus.DRAFT) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Only a DRAFT booking can be checked out (current: " + booking.getStatus() + ")");
        }

        // UC-033: khoá bản ghi PT rồi chi nhánh (thứ tự cố định, tránh deadlock)
        // để tuần tự hoá giữ chỗ — chống double-booking / vượt capacity khi đồng thời.
        if (booking.getPtProfile() != null) {
            ptProfileRepository.lockById(booking.getPtProfile().getId());
        }
        if (booking.getGymBranch() != null) {
            gymBranchRepository.lockById(booking.getGymBranch().getId());
        }
        bookingEligibilityChecker.assertEligible(booking);

        // UC-073: chốt lại số giảm voucher (đề phòng đổi lựa chọn / voucher hết hạn).
        voucherService.recomputeDiscount(booking);
        // UC-034: chốt snapshot giá (đã trừ voucher).
        bookingPriceCalculator.applyPricing(booking);
        // UC-073: tiêu thụ một lượt voucher (khoá + kiểm tra giới hạn).
        voucherService.consumeAtCheckout(booking);

        // UC-035: vào luồng thanh toán; miễn phí thì chuyển thẳng cho Gym (UC-037).
        if (booking.getPayableAmount() != null
                && booking.getPayableAmount().compareTo(java.math.BigDecimal.ZERO) == 0) {
            bookingLifecycle.transition(booking, BookingStatus.PENDING_GYM,
                    "Free booking - routed to gym");
            bookingRepository.save(booking);
        } else {
            bookingLifecycle.transition(booking, BookingStatus.PENDING_PAYMENT,
                    "Checkout submitted - awaiting payment hold");
            bookingRepository.save(booking);
            // UC-052: tạo đơn thanh toán VietQR để khách chuyển khoản.
            paymentService.createOrder(booking);
        }
        log.info("Customer {} checked out booking {} (payable {})",
                customerUsername, bookingId, booking.getPayableAmount());
        return BookingResponse.of(booking);
    }

    @Override
    @Transactional
    public BookingResponse reschedule(String customerUsername, Long bookingId,
                                      java.time.LocalDateTime startAt, java.time.LocalDateTime endAt) {
        Booking booking = bookingRepository.findByIdAndCustomer_Username(bookingId, customerUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
        if (booking.getStatus() != BookingStatus.PENDING_GYM
                && booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Only a PENDING_GYM or CONFIRMED booking can be rescheduled (current: "
                            + booking.getStatus() + ")");
        }
        if (booking.getPtProfile() != null) {
            ptProfileRepository.lockById(booking.getPtProfile().getId());
        }
        if (booking.getGymBranch() != null) {
            gymBranchRepository.lockById(booking.getGymBranch().getId());
        }
        var issues = bookingEligibilityChecker.rescheduleIssues(booking, startAt, endAt);
        if (!issues.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Cannot reschedule: " + String.join("; ", issues));
        }
        String note = "Rescheduled by customer from " + booking.getStartAt() + " to " + startAt;
        booking.setStartAt(startAt);
        booking.setEndAt(endAt);
        bookingLifecycle.recordNote(booking, note);
        bookingRepository.save(booking);
        return BookingResponse.of(booking);
    }

    @Override
    @Transactional
    public BookingResponse cancel(String customerUsername, Long bookingId, String reason) {
        Booking booking = bookingRepository.findByIdAndCustomer_Username(bookingId, customerUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));

        // UC-042/043: hủy CONFIRMED trong cửa sổ mất phí -> đánh dấu hủy muộn.
        if (booking.getStatus() == BookingStatus.CONFIRMED && booking.getStartAt() != null) {
            Integer freeHours = resolveFreeCancellationHours(booking);
            if (freeHours != null) {
                java.time.LocalDateTime deadline = booking.getStartAt().minusHours(freeHours);
                if (java.time.LocalDateTime.now().isAfter(deadline)) {
                    booking.setLateCancellation(true);
                }
            }
        }
        bookingLifecycle.transition(booking, BookingStatus.CANCELLED,
                "Cancelled by customer" + (reason != null ? ": " + reason : ""));
        bookingRepository.save(booking);
        // UC-042/055: booking đã giữ tiền -> mở yêu cầu hoàn (Finance quyết định
        // mức hoàn; hủy muộn được ghi chú để cân nhắc giữ phí theo chính sách).
        refundService.autoCreate(booking, "Customer cancelled"
                + (booking.isLateCancellation() ? " (LATE cancellation - fee may apply)" : "")
                + (reason != null ? ": " + reason : ""));
        // Đơn VietQR chưa trả tiền thì đóng lại (UC-054).
        paymentService.cancelOrderIfPending(bookingId);
        return BookingResponse.of(booking);
    }

    @Override
    @Transactional
    public BookingResponse checkIn(String customerUsername, Long bookingId) {
        Booking booking = bookingRepository.findByIdAndCustomer_Username(bookingId, customerUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
        attendanceSupport.checkIn(booking, "customer " + customerUsername);
        bookingRepository.save(booking);
        return BookingResponse.of(booking);
    }

    private Integer resolveFreeCancellationHours(Booking booking) {
        if (booking.getGymService() != null && booking.getGymService().getBookingRules() != null) {
            return booking.getGymService().getBookingRules().getFreeCancellationHours();
        }
        if (booking.getTrainingPackage() != null && booking.getTrainingPackage().getBookingRules() != null) {
            return booking.getTrainingPackage().getBookingRules().getFreeCancellationHours();
        }
        return null;
    }

    /**
     * Resolve các đích được truyền và bảo đảm tất cả thuộc CÙNG một Gym.
     * currentGym != null (khi cập nhật DRAFT) được dùng làm Gym mặc định.
     */
    private Selection resolveSelection(CreateBookingRequest request, GymProfile currentGym,
                                       String customerUsername) {
        Selection s = new Selection();

        // UC-049: buổi tập từ gói ĐÃ MUA — loại trừ lẫn nhau với mua mới service/package.
        if (request.getCustomerPackageId() != null) {
            if (request.getServiceId() != null || request.getPackageId() != null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "customerPackageId cannot be combined with serviceId/packageId");
            }
            var cp = packageUsageService.requireUsable(request.getCustomerPackageId(), customerUsername);
            s.customerPackage = cp;
            s.trainingPackage = cp.getTrainingPackage();
            s.gym = cp.getTrainingPackage().getGymProfile();
        }

        if (request.getServiceId() != null) {
            s.service = gymServiceRepository.findById(request.getServiceId())
                    .orElseThrow(() -> new ResourceNotFoundException("Gym service", request.getServiceId()));
            s.gym = s.service.getGymProfile();
        }
        if (request.getPackageId() != null) {
            s.trainingPackage = trainingPackageRepository.findById(request.getPackageId())
                    .orElseThrow(() -> new ResourceNotFoundException("Training package", request.getPackageId()));
            s.gym = requireSameGym(s.gym, s.trainingPackage.getGymProfile());
        }
        if (request.getBranchId() != null) {
            s.branch = gymBranchRepository.findById(request.getBranchId())
                    .orElseThrow(() -> new ResourceNotFoundException("Gym branch", request.getBranchId()));
            s.gym = requireSameGym(s.gym, s.branch.getGymProfile());
        }
        if (request.getPtId() != null) {
            s.pt = ptProfileRepository.findById(request.getPtId())
                    .orElseThrow(() -> new ResourceNotFoundException("PT profile", request.getPtId()));
            if (s.pt.getGymProfile() == null) {
                throw new BusinessException(ErrorCode.INVALID_STATE, "PT does not belong to any gym");
            }
            s.gym = requireSameGym(s.gym, s.pt.getGymProfile());
        }

        if (s.gym == null) {
            s.gym = currentGym;
        }
        if (s.gym == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "At least one of serviceId, packageId, branchId or ptId is required");
        }
        if (currentGym != null && !s.gym.getId().equals(currentGym.getId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "All selections must belong to the same gym as the booking");
        }
        return s;
    }

    private GymProfile requireSameGym(GymProfile current, GymProfile candidate) {
        if (current != null && !current.getId().equals(candidate.getId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "All selections must belong to the same gym");
        }
        return candidate;
    }

    private static class Selection {
        GymProfile gym;
        GymService service;
        TrainingPackage trainingPackage;
        com.fitmatch.entity.CustomerPackage customerPackage;
        GymBranch branch;
        PtProfile pt;
    }
}
