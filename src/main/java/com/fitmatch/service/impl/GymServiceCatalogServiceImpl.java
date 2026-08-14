package com.fitmatch.service.impl;

import com.fitmatch.common.enums.CatalogStatus;
import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.dto.gym.GymServiceRequest;
import com.fitmatch.dto.gym.GymServiceResponse;
import com.fitmatch.entity.GymBranch;
import com.fitmatch.entity.GymProfile;
import com.fitmatch.entity.GymService;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.GymBranchRepository;
import com.fitmatch.repository.GymServiceRepository;
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
    private final GymBranchRepository gymBranchRepository;
    private final GymProfileResolver gymProfileResolver;

    @Override
    @Transactional
    public GymServiceResponse create(String username, GymServiceRequest request) {
        GymProfile gym = gymProfileResolver.requireApprovedGym(username);
        GymService saved = gymServiceRepository.save(GymService.builder()
                .gymProfile(gym)
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .status(CatalogStatus.PUBLISHED)
                .active(true)
                .build());
        log.info("Gym {} created service {} ({})", username, saved.getId(), saved.getPrice());
        return GymServiceResponse.of(saved);
    }

    @Override
    @Transactional
    public GymServiceResponse update(String username, Long id, GymServiceRequest request) {
        GymService service = requireOwned(username, id);
        requireNotArchived(service, "edited");
        service.setName(request.getName());
        service.setDescription(request.getDescription());
        service.setPrice(request.getPrice());
        // Vé đã bán giữ nguyên dòng dịch vụ đã snapshot — sửa ở đây chỉ ảnh
        // hưởng lần mua tiếp theo.
        return GymServiceResponse.of(gymServiceRepository.save(service));
    }

    @Override
    @Transactional
    public void deactivate(String username, Long id) {
        GymService service = requireOwned(username, id);
        service.setActive(false);
        service.setStatus(CatalogStatus.HIDDEN);
        gymServiceRepository.save(service);
        log.info("Gym {} deactivated service {}", username, id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GymServiceResponse> list(String username) {
        GymProfile gym = gymProfileResolver.requireApprovedGym(username);
        return gymServiceRepository.findByGymProfile_IdOrderByIdDesc(gym.getId())
                .stream().map(GymServiceResponse::of).toList();
    }

    @Override
    @Transactional
    public GymServiceResponse updateCatalogStatus(String username, Long id, CatalogStatus status) {
        GymService service = requireOwned(username, id);
        requireNotArchived(service, "changed");
        service.setStatus(status);
        service.setActive(status == CatalogStatus.PUBLISHED);
        log.info("Gym {} set service {} catalog status to {}", username, id, status);
        return GymServiceResponse.of(gymServiceRepository.save(service));
    }

    @Override
    @Transactional(readOnly = true)
    public List<GymServiceResponse> listForBranch(Long branchId) {
        GymBranch branch = gymBranchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Gym branch", branchId));
        return gymServiceRepository
                .findByGymProfile_IdAndStatusAndActiveTrueOrderByIdDesc(
                        branch.getGymProfile().getId(), CatalogStatus.PUBLISHED)
                .stream().map(GymServiceResponse::of).toList();
    }

    /** ARCHIVED là trạng thái cuối — khớp cách TicketType xử lý. */
    private void requireNotArchived(GymService service, String action) {
        if (service.getStatus() == CatalogStatus.ARCHIVED) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "An ARCHIVED service cannot be " + action);
        }
    }

    private GymService requireOwned(String username, Long id) {
        return gymServiceRepository.findByIdAndGymProfile_User_Username(id, username)
                .orElseThrow(() -> new ResourceNotFoundException("Gym service", id));
    }
}
