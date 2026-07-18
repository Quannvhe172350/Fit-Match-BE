package com.fitmatch.service;

import com.fitmatch.dto.gym.BranchRequest;
import com.fitmatch.dto.gym.BranchResponse;
import com.fitmatch.entity.GymBranch;
import com.fitmatch.entity.GymProfile;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.GymBranchRepository;
import com.fitmatch.service.impl.GymBranchServiceImpl;
import com.fitmatch.service.support.GymProfileResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GymBranchServiceImplTest {

    @Mock private GymBranchRepository branchRepository;
    @Mock private GymProfileResolver gymProfileResolver;
    @Mock private com.fitmatch.repository.OperatingHourRepository operatingHourRepository;
    @InjectMocks private GymBranchServiceImpl service;

    @Test
    void create_persistsActiveBranch() {
        when(gymProfileResolver.requireApprovedGym("ops")).thenReturn(GymProfile.builder().id(3L).build());
        when(branchRepository.save(any(GymBranch.class))).thenAnswer(inv -> {
            GymBranch b = inv.getArgument(0);
            b.setId(1L);
            return b;
        });

        BranchResponse res = service.create("ops", BranchRequest.builder().name("District 1").build());

        assertThat(res.getName()).isEqualTo("District 1");
        assertThat(res.isActive()).isTrue();
        // UC-017/UC-030: chi nhánh mới được seed giờ hoạt động mặc định cả tuần.
        org.mockito.Mockito.verify(operatingHourRepository).saveAll(org.mockito.ArgumentMatchers.argThat(hours ->
                ((java.util.Collection<?>) hours).size() == 7));
    }

    @Test
    void detail_notOwned_throws() {
        when(branchRepository.findByIdAndGymProfile_User_Username(9L, "ops")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.detail("ops", 9L)).isInstanceOf(ResourceNotFoundException.class);
    }
}
