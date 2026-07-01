package com.fitmatch.service.impl;

import com.fitmatch.dto.gym.BranchRequest;
import com.fitmatch.dto.gym.BranchResponse;
import com.fitmatch.entity.GymBranch;
import com.fitmatch.entity.GymProfile;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.GymBranchRepository;
import com.fitmatch.service.GymBranchService;
import com.fitmatch.service.support.GymProfileResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GymBranchServiceImpl implements GymBranchService {

    private final GymBranchRepository branchRepository;
    private final GymProfileResolver gymProfileResolver;

    @Override
    @Transactional
    public BranchResponse create(String username, BranchRequest request) {
        GymProfile gym = gymProfileResolver.requireApprovedGym(username);
        GymBranch branch = branchRepository.save(GymBranch.builder()
                .gymProfile(gym)
                .name(request.getName())
                .address(request.getAddress())
                .city(request.getCity())
                .phone(request.getPhone())
                .active(true)
                .build());
        log.info("Gym {} created branch {}", username, branch.getId());
        return BranchResponse.of(branch);
    }

    @Override
    @Transactional
    public BranchResponse update(String username, Long id, BranchRequest request) {
        GymBranch branch = requireOwned(username, id);
        branch.setName(request.getName());
        branch.setAddress(request.getAddress());
        branch.setCity(request.getCity());
        branch.setPhone(request.getPhone());
        return BranchResponse.of(branchRepository.save(branch));
    }

    @Override
    @Transactional
    public void deactivate(String username, Long id) {
        GymBranch branch = requireOwned(username, id);
        branch.setActive(false);
        branchRepository.save(branch);
        log.info("Gym {} deactivated branch {}", username, id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BranchResponse> list(String username) {
        GymProfile gym = gymProfileResolver.requireApprovedGym(username);
        return branchRepository.findByGymProfile_Id(gym.getId()).stream().map(BranchResponse::of).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BranchResponse detail(String username, Long id) {
        return BranchResponse.of(requireOwned(username, id));
    }

    private GymBranch requireOwned(String username, Long id) {
        return branchRepository.findByIdAndGymProfile_User_Username(id, username)
                .orElseThrow(() -> new ResourceNotFoundException("Branch", id));
    }
}
