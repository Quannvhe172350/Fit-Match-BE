package com.fitmatch.service.impl;

import com.fitmatch.common.enums.CatalogStatus;
import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.dto.gym.BookingRulesDto;
import com.fitmatch.dto.gym.TrainingPackageRequest;
import com.fitmatch.dto.gym.TrainingPackageResponse;
import com.fitmatch.entity.GymProfile;
import com.fitmatch.entity.GymService;
import com.fitmatch.entity.TrainingPackage;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.GymServiceRepository;
import com.fitmatch.repository.TrainingPackageRepository;
import com.fitmatch.service.TrainingPackageService;
import com.fitmatch.service.support.GymProfileResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrainingPackageServiceImpl implements TrainingPackageService {

    private final TrainingPackageRepository trainingPackageRepository;
    private final GymServiceRepository gymServiceRepository;
    private final GymProfileResolver gymProfileResolver;

    @Override
    @Transactional
    public TrainingPackageResponse create(String username, TrainingPackageRequest request) {
        GymProfile gym = gymProfileResolver.requireApprovedGym(username);
        TrainingPackage pkg = trainingPackageRepository.save(TrainingPackage.builder()
                .gymProfile(gym)
                .gymService(resolveService(username, request.getGymServiceId()))
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .sessionCount(request.getSessionCount())
                .validityDays(request.getValidityDays())
                .usageConditions(request.getUsageConditions())
                .active(true)
                .build());
        log.info("Gym {} created training package {}", username, pkg.getId());
        return TrainingPackageResponse.of(pkg);
    }

    @Override
    @Transactional
    public TrainingPackageResponse update(String username, Long id, TrainingPackageRequest request) {
        TrainingPackage pkg = requireOwned(username, id);
        pkg.setGymService(resolveService(username, request.getGymServiceId()));
        pkg.setName(request.getName());
        pkg.setDescription(request.getDescription());
        pkg.setPrice(request.getPrice());
        pkg.setSessionCount(request.getSessionCount());
        pkg.setValidityDays(request.getValidityDays());
        pkg.setUsageConditions(request.getUsageConditions());
        return TrainingPackageResponse.of(trainingPackageRepository.save(pkg));
    }

    @Override
    @Transactional
    public void deactivate(String username, Long id) {
        TrainingPackage pkg = requireOwned(username, id);
        pkg.setActive(false);
        pkg.setStatus(CatalogStatus.HIDDEN);
        trainingPackageRepository.save(pkg);
        log.info("Gym {} deactivated training package {}", username, id);
    }

    @Override
    @Transactional
    public TrainingPackageResponse updateCatalogStatus(String username, Long id, CatalogStatus status) {
        TrainingPackage pkg = requireOwned(username, id);
        // UC-027: ARCHIVED là trạng thái cuối — không quay lại được.
        if (pkg.getStatus() == CatalogStatus.ARCHIVED) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "An ARCHIVED package cannot change status");
        }
        pkg.setStatus(status);
        pkg.setActive(status == CatalogStatus.PUBLISHED);
        log.info("Gym {} set package {} catalog status to {}", username, id, status);
        return TrainingPackageResponse.of(trainingPackageRepository.save(pkg));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrainingPackageResponse> list(String username) {
        GymProfile gym = gymProfileResolver.requireApprovedGym(username);
        return trainingPackageRepository.findByGymProfile_Id(gym.getId()).stream()
                .map(TrainingPackageResponse::of).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TrainingPackageResponse detail(String username, Long id) {
        return TrainingPackageResponse.of(requireOwned(username, id));
    }

    @Override
    @Transactional
    public TrainingPackageResponse updateBookingRules(String username, Long id, BookingRulesDto rules) {
        TrainingPackage pkg = requireOwned(username, id);
        pkg.setBookingRules(rules.toEntity());
        log.info("Gym {} updated booking rules of package {}", username, id);
        return TrainingPackageResponse.of(trainingPackageRepository.save(pkg));
    }

    /** Dịch vụ gắn với gói phải thuộc chính Gym của operator (chống gắn chéo gym khác). */
    private GymService resolveService(String username, Long gymServiceId) {
        if (gymServiceId == null) {
            return null;
        }
        return gymServiceRepository.findByIdAndGymProfile_User_Username(gymServiceId, username)
                .orElseThrow(() -> new ResourceNotFoundException("Gym service", gymServiceId));
    }

    private TrainingPackage requireOwned(String username, Long id) {
        return trainingPackageRepository.findByIdAndGymProfile_User_Username(id, username)
                .orElseThrow(() -> new ResourceNotFoundException("Training package", id));
    }
}
