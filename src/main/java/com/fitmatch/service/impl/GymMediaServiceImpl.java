package com.fitmatch.service.impl;

import com.fitmatch.dto.gym.GymMediaRequest;
import com.fitmatch.dto.gym.GymMediaResponse;
import com.fitmatch.entity.GymBranch;
import com.fitmatch.entity.GymMedia;
import com.fitmatch.entity.GymProfile;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.GymBranchRepository;
import com.fitmatch.repository.GymMediaRepository;
import com.fitmatch.service.GymMediaService;
import com.fitmatch.service.support.GymProfileResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GymMediaServiceImpl implements GymMediaService {

    private final GymMediaRepository gymMediaRepository;
    private final GymBranchRepository gymBranchRepository;
    private final GymProfileResolver gymProfileResolver;

    @Override
    @Transactional
    public GymMediaResponse add(String username, GymMediaRequest request) {
        GymProfile gym = gymProfileResolver.requireApprovedGym(username);
        GymBranch branch = null;
        if (request.getBranchId() != null) {
            branch = gymBranchRepository.findByIdAndGymProfile_User_Username(request.getBranchId(), username)
                    .orElseThrow(() -> new ResourceNotFoundException("Gym branch", request.getBranchId()));
        }
        GymMedia media = gymMediaRepository.save(GymMedia.builder()
                .gymProfile(gym)
                .gymBranch(branch)
                .url(request.getUrl())
                .caption(request.getCaption())
                .build());
        log.info("Gym {} added media {}", username, media.getId());
        return GymMediaResponse.of(media);
    }

    @Override
    @Transactional
    public void delete(String username, Long mediaId) {
        GymMedia media = gymMediaRepository.findByIdAndGymProfile_User_Username(mediaId, username)
                .orElseThrow(() -> new ResourceNotFoundException("Gym media", mediaId));
        gymMediaRepository.delete(media);
        log.info("Gym {} deleted media {}", username, mediaId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GymMediaResponse> list(String username, Long branchId) {
        GymProfile gym = gymProfileResolver.requireApprovedGym(username);
        List<GymMedia> media = branchId != null
                ? gymMediaRepository.findByGymBranch_Id(branchId)
                : gymMediaRepository.findByGymProfile_Id(gym.getId());
        return media.stream()
                .filter(m -> m.getGymProfile().getId().equals(gym.getId()))
                .map(GymMediaResponse::of).toList();
    }
}
