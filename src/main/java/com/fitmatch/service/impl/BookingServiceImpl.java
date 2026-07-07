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

    @Override
    @Transactional
    public BookingResponse createDraft(String customerUsername, CreateBookingRequest request) {
        User customer = userRepository.findByUsername(customerUsername)
                .orElseThrow(() -> new ResourceNotFoundException("User", customerUsername));

        Selection selection = resolveSelection(request, null);

        Booking booking = bookingRepository.save(Booking.builder()
                .customer(customer)
                .gymProfile(selection.gym)
                .gymService(selection.service)
                .trainingPackage(selection.trainingPackage)
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

        Selection selection = resolveSelection(request, booking.getGymProfile());
        booking.setGymProfile(selection.gym);
        if (request.getServiceId() != null) {
            booking.setGymService(selection.service);
        }
        if (request.getPackageId() != null) {
            booking.setTrainingPackage(selection.trainingPackage);
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

    /**
     * Resolve các đích được truyền và bảo đảm tất cả thuộc CÙNG một Gym.
     * currentGym != null (khi cập nhật DRAFT) được dùng làm Gym mặc định.
     */
    private Selection resolveSelection(CreateBookingRequest request, GymProfile currentGym) {
        Selection s = new Selection();

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
        GymBranch branch;
        PtProfile pt;
    }
}
