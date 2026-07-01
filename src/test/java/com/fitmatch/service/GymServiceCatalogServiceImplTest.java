package com.fitmatch.service;

import com.fitmatch.dto.gym.GymServiceRequest;
import com.fitmatch.dto.gym.GymServiceResponse;
import com.fitmatch.entity.GymProfile;
import com.fitmatch.entity.GymService;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.GymServiceRepository;
import com.fitmatch.service.impl.GymServiceCatalogServiceImpl;
import com.fitmatch.service.support.GymProfileResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GymServiceCatalogServiceImplTest {

    @Mock private GymServiceRepository gymServiceRepository;
    @Mock private GymProfileResolver gymProfileResolver;
    @InjectMocks private GymServiceCatalogServiceImpl service;

    @Test
    void create_persistsActiveService() {
        when(gymProfileResolver.requireApprovedGym("ops")).thenReturn(GymProfile.builder().id(3L).build());
        when(gymServiceRepository.save(any(GymService.class))).thenAnswer(inv -> {
            GymService s = inv.getArgument(0);
            s.setId(1L);
            return s;
        });

        GymServiceResponse res = service.create("ops",
                GymServiceRequest.builder().name("Monthly").price(new BigDecimal("500000")).build());

        assertThat(res.getName()).isEqualTo("Monthly");
        assertThat(res.getPrice()).isEqualByComparingTo("500000");
        assertThat(res.isActive()).isTrue();
    }

    @Test
    void update_notOwned_throws() {
        when(gymServiceRepository.findByIdAndGymProfile_User_Username(9L, "ops")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update("ops", 9L,
                GymServiceRequest.builder().name("X").price(BigDecimal.ONE).build()))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
