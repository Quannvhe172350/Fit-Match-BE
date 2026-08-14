package com.fitmatch.service;

import com.fitmatch.common.enums.PtStatus;
import com.fitmatch.common.enums.SessionStatus;
import com.fitmatch.entity.PtAvailability;
import com.fitmatch.entity.PtProfile;
import com.fitmatch.entity.TrainingSession;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.repository.PtAssignmentRepository;
import com.fitmatch.repository.PtAvailabilityRepository;
import com.fitmatch.repository.PtProfileRepository;
import com.fitmatch.repository.TrainingSessionRepository;
import com.fitmatch.service.support.PtSlotValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/** Bốn điều kiện của một khung giờ PT: ACTIVE, đúng chi nhánh, đã khai, chưa bị đặt. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PtSlotValidatorTest {

    private static final Long PT_ID = 11L;
    private static final Long BRANCH_ID = 22L;
    private static final LocalDate DATE = LocalDate.of(2026, 8, 20);
    private static final LocalTime START = LocalTime.of(18, 0);

    @Mock private PtProfileRepository ptProfileRepository;
    @Mock private PtAssignmentRepository ptAssignmentRepository;
    @Mock private PtAvailabilityRepository ptAvailabilityRepository;
    @Mock private TrainingSessionRepository trainingSessionRepository;
    @InjectMocks private PtSlotValidator validator;

    private PtAvailability slot;

    @BeforeEach
    void happyPath() {
        slot = PtAvailability.builder()
                .id(1L).slotDate(DATE).startTime(START).endTime(LocalTime.of(19, 0)).build();

        when(ptProfileRepository.findById(PT_ID))
                .thenReturn(Optional.of(PtProfile.builder().id(PT_ID).status(PtStatus.ACTIVE).build()));
        when(ptAssignmentRepository.existsByPtProfile_IdAndGymBranch_Id(PT_ID, BRANCH_ID))
                .thenReturn(true);
        when(ptAvailabilityRepository.findByPtProfile_IdAndSlotDateAndStartTime(PT_ID, DATE, START))
                .thenReturn(Optional.of(slot));
        when(trainingSessionRepository.findByPtProfile_IdAndSessionDateAndStatusIn(anyLong(), any(), any()))
                .thenReturn(List.of());
    }

    @Test
    void allConditionsMet_returnsSlotWithEndTime() {
        PtAvailability resolved = validator.resolveSlot(PT_ID, BRANCH_ID, DATE, START, null);

        assertThat(resolved.getEndTime()).isEqualTo(LocalTime.of(19, 0));
    }

    @Test
    void suspendedPt_rejected() {
        when(ptProfileRepository.findById(PT_ID))
                .thenReturn(Optional.of(PtProfile.builder().id(PT_ID).status(PtStatus.SUSPENDED).build()));

        assertThatThrownBy(() -> validator.resolveSlot(PT_ID, BRANCH_ID, DATE, START, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PT hiện không nhận lịch");
    }

    @Test
    void inactivePt_rejected() {
        when(ptProfileRepository.findById(PT_ID))
                .thenReturn(Optional.of(PtProfile.builder().id(PT_ID).status(PtStatus.INACTIVE).build()));

        assertThatThrownBy(() -> validator.resolveSlot(PT_ID, BRANCH_ID, DATE, START, null))
                .isInstanceOf(BusinessException.class);
    }

    /** Câu 24: phân công giờ chỉ còn đích chi nhánh — PT của chi nhánh khác bị từ chối. */
    @Test
    void ptNotAssignedToBranch_rejected() {
        when(ptAssignmentRepository.existsByPtProfile_IdAndGymBranch_Id(PT_ID, BRANCH_ID))
                .thenReturn(false);

        assertThatThrownBy(() -> validator.resolveSlot(PT_ID, BRANCH_ID, DATE, START, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không phụ trách chi nhánh này");
    }

    @Test
    void slotNotDeclaredForThatDay_rejected() {
        when(ptAvailabilityRepository.findByPtProfile_IdAndSlotDateAndStartTime(PT_ID, DATE, START))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> validator.resolveSlot(PT_ID, BRANCH_ID, DATE, START, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không khai khung giờ");
    }

    @Test
    void slotAlreadyTaken_rejected() {
        when(trainingSessionRepository.findByPtProfile_IdAndSessionDateAndStatusIn(anyLong(), any(), any()))
                .thenReturn(List.of(TrainingSession.builder()
                        .id(99L).ptSlotStart(START).status(SessionStatus.SCHEDULED).build()));

        assertThatThrownBy(() -> validator.resolveSlot(PT_ID, BRANCH_ID, DATE, START, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã có người đặt");
    }

    /** Đổi khung giờ trong cùng ngày: chính buổi đang sửa không được tính là kẹt chỗ. */
    @Test
    void excludedSession_doesNotBlockItself() {
        when(trainingSessionRepository.findByPtProfile_IdAndSessionDateAndStatusIn(anyLong(), any(), any()))
                .thenReturn(List.of(TrainingSession.builder()
                        .id(99L).ptSlotStart(START).status(SessionStatus.SCHEDULED).build()));

        PtAvailability resolved = validator.resolveSlot(PT_ID, BRANCH_ID, DATE, START, 99L);

        assertThat(resolved).isNotNull();
    }

    /** Buổi đã huỷ trả lại khung giờ — chỉ SCHEDULED/DONE mới giữ chỗ. */
    @Test
    void holdingStatuses_excludeCancelled() {
        assertThat(PtSlotValidator.HOLDING_STATUSES)
                .containsExactlyInAnyOrder(SessionStatus.SCHEDULED, SessionStatus.DONE);
    }

    @Test
    void otherSlotSameDay_doesNotBlock() {
        when(trainingSessionRepository.findByPtProfile_IdAndSessionDateAndStatusIn(anyLong(), any(), any()))
                .thenReturn(List.of(TrainingSession.builder()
                        .id(99L).ptSlotStart(LocalTime.of(6, 0)).status(SessionStatus.SCHEDULED).build()));

        assertThat(validator.resolveSlot(PT_ID, BRANCH_ID, DATE, START, null)).isNotNull();
    }
}
