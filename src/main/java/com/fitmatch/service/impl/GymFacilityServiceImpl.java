package com.fitmatch.service.impl;

import com.fitmatch.common.enums.MediaEntityType;
import com.fitmatch.common.enums.MediaImageType;
import com.fitmatch.dto.gym.FacilityRequest;
import com.fitmatch.dto.gym.FacilityResponse;
import com.fitmatch.dto.media.MediaResponse;
import com.fitmatch.entity.GymBranch;
import com.fitmatch.entity.GymFacility;
import com.fitmatch.entity.GymProfile;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.GymBranchRepository;
import com.fitmatch.repository.GymFacilityRepository;
import com.fitmatch.service.GymFacilityService;
import com.fitmatch.service.MediaService;
import com.fitmatch.service.support.GymProfileResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GymFacilityServiceImpl implements GymFacilityService {

    private final GymFacilityRepository facilityRepository;
    private final GymBranchRepository gymBranchRepository;
    private final GymProfileResolver gymProfileResolver;
    private final MediaService mediaService;

    @Override
    @Transactional
    public FacilityResponse create(String username, FacilityRequest request) {
        GymProfile gym = gymProfileResolver.requireApprovedGym(username);
        GymFacility facility = facilityRepository.save(GymFacility.builder()
                .gymProfile(gym)
                .gymBranch(resolveBranch(username, request.getBranchId()))
                .name(request.getName())
                .description(request.getDescription())
                .active(true)
                .build());
        log.info("Gym {} created facility {}", username, facility.getId());
        syncImages(username, facility.getId(), request.getMediaIds());
        return FacilityResponse.of(facility, imagesOf(username, facility.getId()));
    }

    @Override
    @Transactional
    public FacilityResponse update(String username, Long id, FacilityRequest request) {
        GymFacility facility = requireOwned(username, id);
        facility.setName(request.getName());
        facility.setDescription(request.getDescription());
        facility.setGymBranch(resolveBranch(username, request.getBranchId()));
        GymFacility saved = facilityRepository.save(facility);
        syncImages(username, id, request.getMediaIds());
        return FacilityResponse.of(saved, imagesOf(username, id));
    }

    /**
     * Đồng bộ thư viện ảnh của facility với danh sách id client gửi lên: ảnh đang
     * gắn mà vắng mặt bị xoá hẳn (cả object trên storage), ảnh nháp mới upload thì
     * được gắn vào. {@code null} = client không quản lý ảnh ở lần gọi này.
     */
    private void syncImages(String username, Long facilityId, List<Long> mediaIds) {
        if (mediaIds == null) return;
        for (MediaResponse existing : imagesOf(username, facilityId)) {
            if (!mediaIds.contains(existing.getId())) {
                mediaService.delete(username, existing.getId());
            }
        }
        mediaService.attach(username, MediaEntityType.FACILITY, facilityId,
                MediaImageType.GALLERY, mediaIds);
    }

    private List<MediaResponse> imagesOf(String username, Long facilityId) {
        return mediaService.list(username, MediaEntityType.FACILITY, facilityId, MediaImageType.GALLERY);
    }

    /** UC-016: chi nhánh gắn với facility phải thuộc chính Gym của operator. */
    private GymBranch resolveBranch(String username, Long branchId) {
        if (branchId == null) {
            return null;
        }
        return gymBranchRepository.findByIdAndGymProfile_User_Username(branchId, username)
                .orElseThrow(() -> new ResourceNotFoundException("Gym branch", branchId));
    }

    @Override
    @Transactional
    public void deactivate(String username, Long id) {
        GymFacility facility = requireOwned(username, id);
        facility.setActive(false);
        facilityRepository.save(facility);
        log.info("Gym {} deactivated facility {}", username, id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FacilityResponse> list(String username) {
        GymProfile gym = gymProfileResolver.requireApprovedGym(username);
        List<GymFacility> facilities = facilityRepository.findByGymProfile_Id(gym.getId());
        // Một truy vấn ảnh cho cả danh sách thay vì mỗi facility một lần (N+1).
        Map<Long, List<MediaResponse>> images = mediaService.listForEntities(
                MediaEntityType.FACILITY,
                facilities.stream().map(GymFacility::getId).toList(),
                MediaImageType.GALLERY);
        return facilities.stream()
                .map(f -> FacilityResponse.of(f, images.get(f.getId())))
                .toList();
    }

    private GymFacility requireOwned(String username, Long id) {
        return facilityRepository.findByIdAndGymProfile_User_Username(id, username)
                .orElseThrow(() -> new ResourceNotFoundException("Facility", id));
    }
}
