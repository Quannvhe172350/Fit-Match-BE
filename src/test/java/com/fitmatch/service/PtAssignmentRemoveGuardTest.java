package com.fitmatch.service;

import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.VerificationStatus;
import com.fitmatch.dto.pt.PtAssignmentRequest;
import com.fitmatch.entity.GymBranch;
import com.fitmatch.entity.GymProfile;
import com.fitmatch.entity.PtAssignment;
import com.fitmatch.entity.PtProfile;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.repository.TrainingSessionRepository;
import com.fitmatch.repository.GymBranchRepository;
import com.fitmatch.repository.PtAssignmentRepository;
import com.fitmatch.repository.PtProfileRepository;
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
    @Mock private TrainingSessionRepository trainingSessionRepository;
    @InjectMocks private PtAssignmentServiceImpl service;

    @Test
    void remove_withUpcomingSessions_blocksAndDoesNotDelete() {
        when(ptProfileRepository.findByIdAndGymProfile_User_Username(2L, "gym"))
                .thenReturn(Optional.of(PtProfile.builder().id(2L).build()));
        when(ptAssignmentRepository.findByIdAndPtProfile_GymProfile_User_Username(7L, "gym"))
                .thenReturn(Optional.of(PtAssignment.builder().id(7L).build()));
        when(trainingSessionRepository.countByPtProfile_IdAndStatusAndSessionDateGreaterThanEqual(eq(2L), any(), any()))
                .thenReturn(3L);

        assertThatThrownBy(() -> service.remove("gym", 2L, 7L))
                .isInstanceOf(BusinessException.class);

        verify(ptAssignmentRepository, never()).delete(any());
    }

    @Test
    void remove_noUpcomingSessions_deletes() {
        when(ptProfileRepository.findByIdAndGymProfile_User_Username(2L, "gym"))
                .thenReturn(Optional.of(PtProfile.builder().id(2L).build()));
        PtAssignment a = PtAssignment.builder().id(7L).build();
        when(ptAssignmentRepository.findByIdAndPtProfile_GymProfile_User_Username(7L, "gym"))
                .thenReturn(Optional.of(a));
        when(trainingSessionRepository.countByPtProfile_IdAndStatusAndSessionDateGreaterThanEqual(eq(2L), any(), any()))
                .thenReturn(0L);

        service.remove("gym", 2L, 7L);

        verify(ptAssignmentRepository).delete(a);
    }

    @Test
    void assign_toDeactivatedBranch_blocked() {
        // Chi nhánh đã ngừng không nhận booking nào, nên phân công vào đó chỉ tạo
        // bản ghi treo — chặn ở BE vì dropdown FE không phải chốt cuối.
        when(ptProfileRepository.findByIdAndGymProfile_User_Username(2L, "gym"))
                .thenReturn(Optional.of(PtProfile.builder().id(2L).build()));
        when(ptAssignmentRepository.existsByPtProfile_IdAndGymBranch_Id(2L, 4L)).thenReturn(false);
        when(gymBranchRepository.findByIdAndGymProfile_User_Username(4L, "gym"))
                .thenReturn(Optional.of(GymBranch.builder().id(4L).active(false).build()));

        PtAssignmentRequest request = PtAssignmentRequest.builder().branchId(4L).build();

        assertThatThrownBy(() -> service.assign("gym", 2L, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_STATE);
        verify(ptAssignmentRepository, never()).save(any());
    }

    @Test
    void assign_toActiveBranch_saves() {
        when(ptProfileRepository.findByIdAndGymProfile_User_Username(2L, "gym"))
                .thenReturn(Optional.of(PtProfile.builder().id(2L).build()));
        when(ptAssignmentRepository.existsByPtProfile_IdAndGymBranch_Id(2L, 4L)).thenReturn(false);
        when(gymBranchRepository.findByIdAndGymProfile_User_Username(4L, "gym"))
                .thenReturn(Optional.of(GymBranch.builder().id(4L).active(true).build()));
        when(ptAssignmentRepository.save(any(PtAssignment.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        PtAssignmentRequest request = PtAssignmentRequest.builder().branchId(4L).build();

        service.assign("gym", 2L, request);

        verify(ptAssignmentRepository).save(any(PtAssignment.class));
    }

    @Test
    void manage_whenGymSuspended_blocked() {
        // P1-1.2: Gym bị đình chỉ (SUSPENDED) không được quản lý PT — requireOwnedPt chặn ngay.
        PtProfile pt = PtProfile.builder().id(2L)
                .gymProfile(GymProfile.builder().id(9L)
                        .verificationStatus(VerificationStatus.SUSPENDED).build())
                .build();
        when(ptProfileRepository.findByIdAndGymProfile_User_Username(2L, "gym"))
                .thenReturn(Optional.of(pt));

        assertThatThrownBy(() -> service.remove("gym", 2L, 7L))
                .isInstanceOf(BusinessException.class);
        verify(ptAssignmentRepository, never()).delete(any());
    }
}
