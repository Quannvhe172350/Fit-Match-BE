package com.fitmatch.service.impl;

import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.dto.gym.GymServiceRequest;
import com.fitmatch.dto.gym.GymServiceResponse;
import com.fitmatch.entity.GymProfile;
import com.fitmatch.entity.GymService;
import com.fitmatch.entity.ServiceCategory;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.GymServiceRepository;
import com.fitmatch.repository.ServiceCategoryRepository;
import com.fitmatch.service.GymServiceCatalogService;
import com.fitmatch.service.support.GymProfileResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GymServiceCatalogServiceImpl implements GymServiceCatalogService {

    private final GymServiceRepository gymServiceRepository;
    private final ServiceCategoryRepository serviceCategoryRepository;
    private final GymProfileResolver gymProfileResolver;

    @Override
    @Transactional
    public GymServiceResponse create(String username, GymServiceRequest request) {
        GymProfile gym = gymProfileResolver.requireApprovedGym(username);
        GymService svc = gymServiceRepository.save(GymService.builder()
                .gymProfile(gym)
                .name(request.getName())
                .description(request.getDescription())
                .category(resolveCategory(request.getCategoryId()))
                .eligibilityNotes(request.getEligibilityNotes())
                .price(request.getPrice())
                .durationMinutes(request.getDurationMinutes())
                .active(true)
                .build());
        log.info("Gym {} created service {}", username, svc.getId());
        return GymServiceResponse.of(svc);
    }

    @Override
    @Transactional
    public GymServiceResponse update(String username, Long id, GymServiceRequest request) {
        GymService svc = requireOwned(username, id);
        svc.setName(request.getName());
        svc.setDescription(request.getDescription());
        svc.setCategory(resolveCategory(request.getCategoryId()));
        svc.setEligibilityNotes(request.getEligibilityNotes());
        svc.setPrice(request.getPrice());
        svc.setDurationMinutes(request.getDurationMinutes());
        return GymServiceResponse.of(gymServiceRepository.save(svc));
    }

    /** UC-024: danh mục phải tồn tại và đang bật; null = không phân loại. */
    private ServiceCategory resolveCategory(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        ServiceCategory category = serviceCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Service category", categoryId));
        if (!category.isActive()) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Service category '" + category.getName() + "' is inactive");
        }
        return category;
    }

    @Override
    @Transactional
    public void deactivate(String username, Long id) {
        GymService svc = requireOwned(username, id);
        svc.setActive(false);
        gymServiceRepository.save(svc);
        log.info("Gym {} deactivated service {}", username, id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GymServiceResponse> list(String username) {
        GymProfile gym = gymProfileResolver.requireApprovedGym(username);
        return gymServiceRepository.findByGymProfile_Id(gym.getId()).stream()
                .map(GymServiceResponse::of).toList();
    }

    private GymService requireOwned(String username, Long id) {
        return gymServiceRepository.findByIdAndGymProfile_User_Username(id, username)
                .orElseThrow(() -> new ResourceNotFoundException("Gym service", id));
    }
}
