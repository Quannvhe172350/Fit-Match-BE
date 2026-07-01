package com.fitmatch.service.impl;

import com.fitmatch.dto.gym.FacilityRequest;
import com.fitmatch.dto.gym.FacilityResponse;
import com.fitmatch.entity.GymFacility;
import com.fitmatch.entity.GymProfile;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.GymFacilityRepository;
import com.fitmatch.service.GymFacilityService;
import com.fitmatch.service.support.GymProfileResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GymFacilityServiceImpl implements GymFacilityService {

    private final GymFacilityRepository facilityRepository;
    private final GymProfileResolver gymProfileResolver;

    @Override
    @Transactional
    public FacilityResponse create(String username, FacilityRequest request) {
        GymProfile gym = gymProfileResolver.requireApprovedGym(username);
        GymFacility facility = facilityRepository.save(GymFacility.builder()
                .gymProfile(gym)
                .name(request.getName())
                .description(request.getDescription())
                .active(true)
                .build());
        log.info("Gym {} created facility {}", username, facility.getId());
        return FacilityResponse.of(facility);
    }

    @Override
    @Transactional
    public FacilityResponse update(String username, Long id, FacilityRequest request) {
        GymFacility facility = requireOwned(username, id);
        facility.setName(request.getName());
        facility.setDescription(request.getDescription());
        return FacilityResponse.of(facilityRepository.save(facility));
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
        return facilityRepository.findByGymProfile_Id(gym.getId()).stream()
                .map(FacilityResponse::of).toList();
    }

    private GymFacility requireOwned(String username, Long id) {
        return facilityRepository.findByIdAndGymProfile_User_Username(id, username)
                .orElseThrow(() -> new ResourceNotFoundException("Facility", id));
    }
}
