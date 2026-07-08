package com.fitmatch.service.impl;

import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.dto.booking.WaitlistRequest;
import com.fitmatch.dto.booking.WaitlistResponse;
import com.fitmatch.entity.GymService;
import com.fitmatch.entity.TrainingPackage;
import com.fitmatch.entity.User;
import com.fitmatch.entity.WaitlistEntry;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.GymServiceRepository;
import com.fitmatch.repository.TrainingPackageRepository;
import com.fitmatch.repository.UserRepository;
import com.fitmatch.repository.WaitlistEntryRepository;
import com.fitmatch.service.WaitlistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WaitlistServiceImpl implements WaitlistService {

    private final WaitlistEntryRepository waitlistEntryRepository;
    private final UserRepository userRepository;
    private final GymServiceRepository gymServiceRepository;
    private final TrainingPackageRepository trainingPackageRepository;

    @Override
    @Transactional
    public WaitlistResponse join(String customerUsername, WaitlistRequest request) {
        boolean hasService = request.getServiceId() != null;
        boolean hasPackage = request.getPackageId() != null;
        if (hasService == hasPackage) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Exactly one of serviceId or packageId must be provided");
        }
        User customer = userRepository.findByUsername(customerUsername)
                .orElseThrow(() -> new ResourceNotFoundException("User", customerUsername));

        WaitlistEntry.WaitlistEntryBuilder builder = WaitlistEntry.builder()
                .customer(customer)
                .preferredStart(request.getPreferredStart())
                .note(request.getNote())
                .active(true);
        if (hasService) {
            GymService service = gymServiceRepository.findById(request.getServiceId())
                    .orElseThrow(() -> new ResourceNotFoundException("Gym service", request.getServiceId()));
            builder.gymService(service);
        } else {
            TrainingPackage pkg = trainingPackageRepository.findById(request.getPackageId())
                    .orElseThrow(() -> new ResourceNotFoundException("Training package", request.getPackageId()));
            builder.trainingPackage(pkg);
        }
        WaitlistEntry saved = waitlistEntryRepository.save(builder.build());
        log.info("Customer {} joined waitlist {}", customerUsername, saved.getId());
        return WaitlistResponse.of(saved);
    }

    @Override
    @Transactional
    public void leave(String customerUsername, Long entryId) {
        WaitlistEntry entry = waitlistEntryRepository.findByIdAndCustomer_Username(entryId, customerUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Waitlist entry", entryId));
        entry.setActive(false);
        waitlistEntryRepository.save(entry);
        log.info("Customer {} left waitlist {}", customerUsername, entryId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WaitlistResponse> myEntries(String customerUsername) {
        return waitlistEntryRepository.findByCustomer_UsernameAndActiveTrueOrderByCreatedAtDesc(customerUsername)
                .stream().map(WaitlistResponse::of).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<WaitlistResponse> listForGym(String gymUsername, Long serviceId, Long packageId) {
        if (serviceId != null) {
            gymServiceRepository.findByIdAndGymProfile_User_Username(serviceId, gymUsername)
                    .orElseThrow(() -> new ResourceNotFoundException("Gym service", serviceId));
            return waitlistEntryRepository.findByGymService_IdAndActiveTrueOrderByCreatedAtAsc(serviceId)
                    .stream().map(WaitlistResponse::of).toList();
        }
        if (packageId != null) {
            trainingPackageRepository.findByIdAndGymProfile_User_Username(packageId, gymUsername)
                    .orElseThrow(() -> new ResourceNotFoundException("Training package", packageId));
            return waitlistEntryRepository.findByTrainingPackage_IdAndActiveTrueOrderByCreatedAtAsc(packageId)
                    .stream().map(WaitlistResponse::of).toList();
        }
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "serviceId or packageId is required");
    }
}
