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
    // UC-18 (V55): geocode fail-soft — mock trả null/none nên chi nhánh vẫn lưu bình thường.
    @Mock private com.fitmatch.service.support.AddressGeocoder addressGeocoder;
    @InjectMocks private GymBranchServiceImpl service;

    @org.junit.jupiter.api.BeforeEach
    void geocoderReturnsNothing() {
        org.mockito.Mockito.lenient()
                .when(addressGeocoder.resolve(any(), any(), any(), any(), any()))
                .thenReturn(com.fitmatch.service.support.AddressGeocoder.Resolution.none());
    }

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

    // ----- UC-18 (V55): geocoding khi sửa chi nhánh -----

    private GymBranch geocodedBranch() {
        return GymBranch.builder()
                .id(4L).name("Hoan Kiem").address("1 Dinh Tien Hoang").city("Ha Noi").district("Hoan Kiem")
                .latitude(new java.math.BigDecimal("21.0290")).longitude(new java.math.BigDecimal("105.8526"))
                .build();
    }

    /** Sửa số điện thoại mà địa chỉ không đổi thì KHÔNG được đốt thêm một lượt gọi Google. */
    @Test
    void update_addressUnchanged_doesNotReGeocode() {
        when(branchRepository.findByIdAndGymProfile_User_Username(4L, "ops"))
                .thenReturn(Optional.of(geocodedBranch()));
        when(branchRepository.save(any(GymBranch.class))).thenAnswer(inv -> inv.getArgument(0));

        service.update("ops", 4L, BranchRequest.builder()
                .name("Hoan Kiem").address("1 Dinh Tien Hoang").city("Ha Noi").district("Hoan Kiem")
                .phone("0909000111").build());

        org.mockito.Mockito.verify(addressGeocoder, org.mockito.Mockito.never())
                .resolve(any(), any(), any(), any(), any());
    }

    @Test
    void update_addressChanged_reGeocodes() {
        when(branchRepository.findByIdAndGymProfile_User_Username(4L, "ops"))
                .thenReturn(Optional.of(geocodedBranch()));
        when(branchRepository.save(any(GymBranch.class))).thenAnswer(inv -> inv.getArgument(0));

        service.update("ops", 4L, BranchRequest.builder()
                .name("Hoan Kiem").address("2 Le Thai To").city("Ha Noi").district("Hoan Kiem").build());

        org.mockito.Mockito.verify(addressGeocoder).resolve(any(), any(), any(), any(), any());
    }

    /** Chi nhánh chưa có toạ độ phải được thử lại, kể cả khi địa chỉ giữ nguyên. */
    @Test
    void update_missingCoordinates_retriesGeocoding() {
        GymBranch branch = geocodedBranch();
        branch.setLatitude(null);
        branch.setLongitude(null);
        when(branchRepository.findByIdAndGymProfile_User_Username(4L, "ops")).thenReturn(Optional.of(branch));
        when(branchRepository.save(any(GymBranch.class))).thenAnswer(inv -> inv.getArgument(0));

        service.update("ops", 4L, BranchRequest.builder()
                .name("Hoan Kiem").address("1 Dinh Tien Hoang").city("Ha Noi").district("Hoan Kiem").build());

        org.mockito.Mockito.verify(addressGeocoder).resolve(any(), any(), any(), any(), any());
    }

    /** Geocode hỏng KHÔNG được xoá trắng toạ độ đang có. */
    @Test
    void update_geocodingFails_keepsExistingCoordinates() {
        when(branchRepository.findByIdAndGymProfile_User_Username(4L, "ops"))
                .thenReturn(Optional.of(geocodedBranch()));
        when(branchRepository.save(any(GymBranch.class))).thenAnswer(inv -> inv.getArgument(0));

        BranchResponse res = service.update("ops", 4L, BranchRequest.builder()
                .name("Hoan Kiem").address("2 Le Thai To").city("Ha Noi").district("Hoan Kiem").build());

        assertThat(res.getLatitude()).isEqualByComparingTo("21.0290");
        assertThat(res.getLongitude()).isEqualByComparingTo("105.8526");
    }
}
