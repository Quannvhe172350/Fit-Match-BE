package com.fitmatch.service;

import com.fitmatch.entity.PtAssignment;
import com.fitmatch.entity.PtProfile;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.repository.BookingRepository;
import com.fitmatch.repository.GymBranchRepository;
import com.fitmatch.repository.GymServiceRepository;
import com.fitmatch.repository.PtAssignmentRepository;
import com.fitmatch.repository.PtProfileRepository;
import com.fitmatch.repository.TrainingPackageRepository;
import com.fitmatch.service.impl.PtAssignmentServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** P1-16: không gỡ phân công PT khi còn booking giữ chỗ tương lai. */
@ExtendWith(MockitoExtension.class)
class PtAssignmentRemoveGuardTest {

    @Mock private PtAssignmentRepository ptAssignmentRepository;
    @Mock private PtProfileRepository ptProfileRepository;
    @Mock private GymBranchRepository gymBranchRepository;
    @Mock private GymServiceRepository gymServiceRepository;
    @Mock private TrainingPackageRepository trainingPackageRepository;
    @Mock private BookingRepository bookingRepository;
    @InjectMocks private PtAssignmentServiceImpl service;

    @Test
    void remove_withUpcomingBookings_blocksAndDoesNotDelete() {
        when(ptProfileRepository.findByIdAndGymProfile_User_Username(2L, "gym"))
                .thenReturn(Optional.of(PtProfile.builder().id(2L).build()));
        when(ptAssignmentRepository.findByIdAndPtProfile_GymProfile_User_Username(7L, "gym"))
                .thenReturn(Optional.of(PtAssignment.builder().id(7L).build()));
        when(bookingRepository.countByPtProfile_IdAndStatusInAndStartAtGreaterThan(eq(2L), any(), any()))
                .thenReturn(3L);

        assertThatThrownBy(() -> service.remove("gym", 2L, 7L))
                .isInstanceOf(BusinessException.class);

        verify(ptAssignmentRepository, never()).delete(any());
    }

    @Test
    void remove_noUpcomingBookings_deletes() {
        when(ptProfileRepository.findByIdAndGymProfile_User_Username(2L, "gym"))
                .thenReturn(Optional.of(PtProfile.builder().id(2L).build()));
        PtAssignment a = PtAssignment.builder().id(7L).build();
        when(ptAssignmentRepository.findByIdAndPtProfile_GymProfile_User_Username(7L, "gym"))
                .thenReturn(Optional.of(a));
        when(bookingRepository.countByPtProfile_IdAndStatusInAndStartAtGreaterThan(eq(2L), any(), any()))
                .thenReturn(0L);

        service.remove("gym", 2L, 7L);

        verify(ptAssignmentRepository).delete(a);
    }
}
