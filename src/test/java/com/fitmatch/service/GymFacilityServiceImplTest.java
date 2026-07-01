package com.fitmatch.service;

import com.fitmatch.dto.gym.FacilityRequest;
import com.fitmatch.dto.gym.FacilityResponse;
import com.fitmatch.entity.GymFacility;
import com.fitmatch.entity.GymProfile;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.GymFacilityRepository;
import com.fitmatch.service.impl.GymFacilityServiceImpl;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GymFacilityServiceImplTest {

    @Mock private GymFacilityRepository facilityRepository;
    @Mock private GymProfileResolver gymProfileResolver;
    @InjectMocks private GymFacilityServiceImpl service;

    @Test
    void create_persistsActiveFacility() {
        GymProfile gym = GymProfile.builder().id(3L).build();
        when(gymProfileResolver.requireApprovedGym("ops")).thenReturn(gym);
        when(facilityRepository.save(any(GymFacility.class))).thenAnswer(inv -> {
            GymFacility f = inv.getArgument(0);
            f.setId(1L);
            return f;
        });

        FacilityResponse res = service.create("ops", FacilityRequest.builder().name("Pool").build());

        assertThat(res.getName()).isEqualTo("Pool");
        assertThat(res.isActive()).isTrue();
    }

    @Test
    void deactivate_notOwned_throws() {
        when(facilityRepository.findByIdAndGymProfile_User_Username(9L, "ops")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.deactivate("ops", 9L)).isInstanceOf(ResourceNotFoundException.class);
        verify(facilityRepository, never()).save(any());
    }
}
