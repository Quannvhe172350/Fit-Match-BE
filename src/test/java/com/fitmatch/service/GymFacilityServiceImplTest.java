package com.fitmatch.service;

import com.fitmatch.common.enums.MediaEntityType;
import com.fitmatch.common.enums.MediaImageType;
import com.fitmatch.dto.gym.FacilityRequest;
import com.fitmatch.dto.gym.FacilityResponse;
import com.fitmatch.dto.media.MediaResponse;
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

import java.util.List;
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
    @Mock private MediaService mediaService;
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
    void create_attachesUploadedImages() {
        GymProfile gym = GymProfile.builder().id(3L).build();
        when(gymProfileResolver.requireApprovedGym("ops")).thenReturn(gym);
        when(facilityRepository.save(any(GymFacility.class))).thenAnswer(inv -> {
            GymFacility f = inv.getArgument(0);
            f.setId(7L);
            return f;
        });
        when(mediaService.list("ops", MediaEntityType.FACILITY, 7L, MediaImageType.GALLERY))
                .thenReturn(List.of(MediaResponse.builder().id(5L).url("https://cdn/pool.jpg").primary(true).build()));

        FacilityResponse res = service.create("ops",
                FacilityRequest.builder().name("Pool").mediaIds(List.of(5L)).build());

        // Ảnh nháp phải được gắn vào facility vừa tạo, và card lấy được thumbnail ngay.
        verify(mediaService).attach("ops", MediaEntityType.FACILITY, 7L, MediaImageType.GALLERY, List.of(5L));
        assertThat(res.getImageUrl()).isEqualTo("https://cdn/pool.jpg");
        assertThat(res.getImages()).hasSize(1);
    }

    @Test
    void update_dropsImagesMissingFromRequest() {
        GymFacility facility = GymFacility.builder().id(7L).name("Pool").active(true).build();
        when(facilityRepository.findByIdAndGymProfile_User_Username(7L, "ops")).thenReturn(Optional.of(facility));
        when(facilityRepository.save(any(GymFacility.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mediaService.list("ops", MediaEntityType.FACILITY, 7L, MediaImageType.GALLERY))
                .thenReturn(List.of(
                        MediaResponse.builder().id(5L).url("https://cdn/keep.jpg").build(),
                        MediaResponse.builder().id(6L).url("https://cdn/drop.jpg").build()));

        service.update("ops", 7L, FacilityRequest.builder().name("Pool").mediaIds(List.of(5L)).build());

        // mediaIds là trạng thái cuối cùng: ảnh vắng mặt bị xoá hẳn, không để lại rác.
        verify(mediaService).delete("ops", 6L);
        verify(mediaService, never()).delete("ops", 5L);
    }

    @Test
    void update_nullMediaIds_keepsImages() {
        GymFacility facility = GymFacility.builder().id(7L).name("Pool").active(true).build();
        when(facilityRepository.findByIdAndGymProfile_User_Username(7L, "ops")).thenReturn(Optional.of(facility));
        when(facilityRepository.save(any(GymFacility.class))).thenAnswer(inv -> inv.getArgument(0));

        service.update("ops", 7L, FacilityRequest.builder().name("Pool").build());

        verify(mediaService, never()).delete(any(), any());
        verify(mediaService, never()).attach(any(), any(), any(), any(), any());
    }

    @Test
    void deactivate_notOwned_throws() {
        when(facilityRepository.findByIdAndGymProfile_User_Username(9L, "ops")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.deactivate("ops", 9L)).isInstanceOf(ResourceNotFoundException.class);
        verify(facilityRepository, never()).save(any());
    }
}
