package com.fitmatch.service;

import com.fitmatch.dto.measurement.BodyMeasurementRequest;
import com.fitmatch.dto.measurement.BodyMeasurementResponse;
import com.fitmatch.entity.BodyMeasurement;
import com.fitmatch.entity.User;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.BodyMeasurementRepository;
import com.fitmatch.repository.UserRepository;
import com.fitmatch.service.impl.BodyMeasurementServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BodyMeasurementServiceImplTest {

    @Mock private BodyMeasurementRepository bodyMeasurementRepository;
    @Mock private UserRepository userRepository;
    @InjectMocks private BodyMeasurementServiceImpl service;

    private BodyMeasurementRequest request(BigDecimal weight, BigDecimal height) {
        return BodyMeasurementRequest.builder()
                .measuredAt(LocalDate.of(2026, 7, 20))
                .weightKg(weight)
                .heightCm(height)
                .build();
    }

    @Test
    void create_savesForCurrentUser_andComputesBmi() {
        User user = User.builder().id(1L).username("john").build();
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(user));
        when(bodyMeasurementRepository.save(any(BodyMeasurement.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        BodyMeasurementResponse res = service.create("john",
                request(new BigDecimal("72.5"), new BigDecimal("175")));

        // BMI = 72.5 / 1.75² = 23.7 (làm tròn 1 chữ số)
        assertThat(res.getBmi()).isEqualByComparingTo(new BigDecimal("23.7"));
        assertThat(res.getMeasuredAt()).isEqualTo(LocalDate.of(2026, 7, 20));
        verify(bodyMeasurementRepository).save(any(BodyMeasurement.class));
    }

    @Test
    void create_withoutHeight_hasNoBmi() {
        User user = User.builder().id(1L).username("john").build();
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(user));
        when(bodyMeasurementRepository.save(any(BodyMeasurement.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        BodyMeasurementResponse res = service.create("john",
                request(new BigDecimal("72.5"), null));

        assertThat(res.getBmi()).isNull();
    }

    @Test
    void update_notOwned_throws404_withoutSaving() {
        when(bodyMeasurementRepository.findByIdAndUser_Username(9L, "mallory"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update("mallory", 9L,
                request(new BigDecimal("70"), null)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(bodyMeasurementRepository, never()).save(any());
    }

    @Test
    void delete_notOwned_throws404_withoutDeleting() {
        when(bodyMeasurementRepository.findByIdAndUser_Username(9L, "mallory"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete("mallory", 9L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(bodyMeasurementRepository, never()).delete(any());
    }

    @Test
    void delete_owned_deletes() {
        BodyMeasurement m = BodyMeasurement.builder().id(9L).build();
        when(bodyMeasurementRepository.findByIdAndUser_Username(9L, "john"))
                .thenReturn(Optional.of(m));

        service.delete("john", 9L);

        verify(bodyMeasurementRepository).delete(m);
    }
}
