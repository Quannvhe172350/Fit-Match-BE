package com.fitmatch.service;

import com.fitmatch.dto.pt.PtAssignmentRequest;
import com.fitmatch.entity.GymBranch;
import com.fitmatch.entity.PtAssignment;
import com.fitmatch.entity.PtProfile;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.repository.TrainingSessionRepository;
import com.fitmatch.repository.GymBranchRepository;
import com.fitmatch.repository.PtAssignmentRepository;
import com.fitmatch.repository.PtProfileRepository;
import com.fitmatch.repository.PtShiftAssignmentRepository;
import com.fitmatch.service.impl.PtAssignmentServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UC-022: PT phải luôn thuộc ít nhất một chi nhánh, và chỉ gán được vào chi
 * nhánh đang hoạt động.
 */
@ExtendWith(MockitoExtension.class)
class PtBranchAssignmentRulesTest {

    @Mock private PtAssignmentRepository ptAssignmentRepository;
    @Mock private PtProfileRepository ptProfileRepository;
    @Mock private GymBranchRepository gymBranchRepository;
    @Mock private TrainingSessionRepository trainingSessionRepository;
    @Mock private PtShiftAssignmentRepository ptShiftAssignmentRepository;
    @InjectMocks private PtAssignmentServiceImpl service;

    private void ownedPt() {
        when(ptProfileRepository.findByIdAndGymProfile_User_Username(2L, "gym"))
                .thenReturn(Optional.of(PtProfile.builder().id(2L).build()));
    }

    @Test
    void assign_toDeactivatedBranch_blocked() {
        ownedPt();
        when(ptAssignmentRepository.existsByPtProfile_IdAndGymBranch_Id(2L, 5L)).thenReturn(false);
        when(gymBranchRepository.findByIdAndGymProfile_User_Username(5L, "gym"))
                .thenReturn(Optional.of(GymBranch.builder().id(5L).name("CN cũ").active(false).build()));

        assertThatThrownBy(() -> service.assign("gym", 2L, req(5L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("deactivated branch");

        verify(ptAssignmentRepository, never()).save(any());
    }

    @Test
    void assign_toActiveBranch_saves() {
        ownedPt();
        when(ptAssignmentRepository.existsByPtProfile_IdAndGymBranch_Id(2L, 5L)).thenReturn(false);
        GymBranch branch = GymBranch.builder().id(5L).name("CN chính").active(true).build();
        when(gymBranchRepository.findByIdAndGymProfile_User_Username(5L, "gym")).thenReturn(Optional.of(branch));
        when(ptAssignmentRepository.save(any()))
                .thenAnswer(inv -> {
                    PtAssignment a = inv.getArgument(0);
                    a.setId(11L);
                    return a;
                });

        var response = service.assign("gym", 2L, req(5L));

        assertThat(response.getId()).isEqualTo(11L);
        verify(ptAssignmentRepository).save(any());
    }

    @Test
    void remove_lastBranch_blocked() {
        ownedPt();
        when(ptAssignmentRepository.findByIdAndPtProfile_GymProfile_User_Username(7L, "gym"))
                .thenReturn(Optional.of(PtAssignment.builder().id(7L)
                        .gymBranch(GymBranch.builder().id(5L).build()).build()));
        when(ptAssignmentRepository.countByPtProfile_Id(2L)).thenReturn(1L);

        assertThatThrownBy(() -> service.remove("gym", 2L, 7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("last branch");

        verify(ptAssignmentRepository, never()).delete(any());
    }

    @Test
    void remove_branch_whenAnotherRemains_deletes() {
        ownedPt();
        PtAssignment a = PtAssignment.builder().id(7L)
                .gymBranch(GymBranch.builder().id(5L).build()).build();
        when(ptAssignmentRepository.findByIdAndPtProfile_GymProfile_User_Username(7L, "gym"))
                .thenReturn(Optional.of(a));
        when(ptAssignmentRepository.countByPtProfile_Id(2L)).thenReturn(2L);
        when(trainingSessionRepository.countByPtProfile_IdAndStatusAndSessionDateGreaterThanEqual(eq(2L), any(), any()))
                .thenReturn(0L);

        service.remove("gym", 2L, 7L);

        verify(ptAssignmentRepository).delete(a);
    }

    /** Gỡ phân công DỊCH VỤ không đụng luật chi nhánh. */
    @Test
    void remove_serviceAssignment_notBlockedByBranchRule() {
        ownedPt();
        PtAssignment a = PtAssignment.builder().id(8L).build();
        when(ptAssignmentRepository.findByIdAndPtProfile_GymProfile_User_Username(8L, "gym"))
                .thenReturn(Optional.of(a));
        when(trainingSessionRepository.countByPtProfile_IdAndStatusAndSessionDateGreaterThanEqual(eq(2L), any(), any()))
                .thenReturn(0L);

        service.remove("gym", 2L, 8L);

        verify(ptAssignmentRepository).delete(a);
    }

    private PtAssignmentRequest req(Long branchId) {
        PtAssignmentRequest r = new PtAssignmentRequest();
        r.setBranchId(branchId);
        return r;
    }
}
