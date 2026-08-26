package com.fitmatch.service;

import com.fitmatch.common.enums.LeaveScope;
import com.fitmatch.common.enums.LeaveStatus;
import com.fitmatch.common.enums.PtStatus;
import com.fitmatch.common.enums.SessionStatus;
import com.fitmatch.entity.GymBranch;
import com.fitmatch.entity.GymShift;
import com.fitmatch.entity.PtLeaveRequest;
import com.fitmatch.entity.PtProfile;
import com.fitmatch.entity.PtShiftAssignment;
import com.fitmatch.entity.TrainingSession;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.repository.PtAssignmentRepository;
import com.fitmatch.repository.PtLeaveRequestRepository;
import com.fitmatch.repository.PtProfileRepository;
import com.fitmatch.repository.PtShiftAssignmentRepository;
import com.fitmatch.repository.TrainingSessionRepository;
import com.fitmatch.service.support.PtSlotValidator;
import com.fitmatch.service.support.ShiftSlotResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * V85 đổi điều kiện #3 từ "PT đã tự khai khung giờ" thành "PT có CA do Gym xếp
 * phủ khung giờ đó", và thêm điều kiện #4 "không có đơn nghỉ đã duyệt".
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PtSlotValidatorTest {

    private static final Long PT_ID = 11L;
    private static final Long BRANCH_ID = 22L;
    private static final LocalDate DATE = LocalDate.of(2026, 8, 20);
    private static final LocalTime START = LocalTime.of(18, 0);

    @Mock private PtProfileRepository ptProfileRepository;
    @Mock private PtAssignmentRepository ptAssignmentRepository;
    @Mock private PtShiftAssignmentRepository shiftAssignmentRepository;
    @Mock private PtLeaveRequestRepository leaveRequestRepository;
    @Mock private TrainingSessionRepository trainingSessionRepository;
    /** Toán học slot là logic thuần, mock nó chỉ làm test kiểm chính cái mock. */
    @Spy private ShiftSlotResolver slotResolver = new ShiftSlotResolver();
    @InjectMocks private PtSlotValidator validator;

    private GymShift eveningShift;

    @BeforeEach
    void happyPath() {
        eveningShift = GymShift.builder()
                .id(5L)
                .gymBranch(GymBranch.builder().id(BRANCH_ID).name("Chi nhánh 1").build())
                .name("Ca tối")
                .startTime(LocalTime.of(18, 0))
                .endTime(LocalTime.of(22, 0))
                .slotMinutes(60)
                .daysOfWeek("1,2,3,4,5,6,7")
                .active(true)
                .build();

        when(ptProfileRepository.findById(PT_ID))
                .thenReturn(Optional.of(PtProfile.builder().id(PT_ID).status(PtStatus.ACTIVE).build()));
        when(ptAssignmentRepository.existsByPtProfile_IdAndGymBranch_Id(PT_ID, BRANCH_ID))
                .thenReturn(true);
        when(shiftAssignmentRepository.findActiveByPtAndDate(PT_ID, DATE))
                .thenReturn(List.of(PtShiftAssignment.builder()
                        .id(1L).gymShift(eveningShift).workDate(DATE).active(true).build()));
        when(leaveRequestRepository.findOverlapping(anyLong(), any(), any(), any()))
                .thenReturn(List.of());
        when(trainingSessionRepository.findByPtProfile_IdAndSessionDateAndStatusIn(anyLong(), any(), any()))
                .thenReturn(List.of());
    }

    // ---------- V93: độ dài buổi ghi trên vé ----------

    /**
     * Vé quy định mỗi buổi 90 phút mà ca chỉ có slot 60 phút -> từ chối, kèm cả
     * hai con số. Không có ràng buộc này thì con số trên vé chỉ là chữ trang trí:
     * khách mua "gói 90 phút" rồi đặt được toàn ca 60 phút.
     */
    @Test
    void ticketRequiresLongerSession_rejected() {
        assertThatThrownBy(() ->
                validator.resolveSlot(PT_ID, BRANCH_ID, DATE, START, null, 90))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("90 phút")
                .hasMessageContaining("60 phút");
    }

    /** Khớp đúng độ dài thì đặt bình thường. */
    @Test
    void ticketMatchesSlotLength_accepted() {
        PtSlotValidator.ResolvedSlot resolved =
                validator.resolveSlot(PT_ID, BRANCH_ID, DATE, START, null, 60);

        assertThat(resolved.endTime()).isEqualTo(LocalTime.of(19, 0));
    }

    /**
     * Ca DÀI HƠN vé cũng bị từ chối: vé 60 phút mà ca 120 phút thì khách chiếm
     * trọn khung hai tiếng của PT trong khi chỉ trả tiền một tiếng.
     */
    @Test
    void slotLongerThanTicket_rejected() {
        eveningShift.setSlotMinutes(120);

        assertThatThrownBy(() ->
                validator.resolveSlot(PT_ID, BRANCH_ID, DATE, START, null, 60))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("120 phút");
    }

    /** Vé không khai độ dài -> giữ nguyên hành vi trước V93, nhận mọi ca. */
    @Test
    void ticketWithoutRequiredMinutes_acceptsAnySlot() {
        PtSlotValidator.ResolvedSlot resolved =
                validator.resolveSlot(PT_ID, BRANCH_ID, DATE, START, null, null);

        assertThat(resolved.endTime()).isEqualTo(LocalTime.of(19, 0));
    }

    /** Giờ kết thúc do CA quyết (slotMinutes), không còn do PT khai. */
    @Test
    void allConditionsMet_returnsSlotEndFromShift() {
        PtSlotValidator.ResolvedSlot resolved = validator.resolveSlot(PT_ID, BRANCH_ID, DATE, START, null);

        assertThat(resolved.endTime()).isEqualTo(LocalTime.of(19, 0));
        assertThat(resolved.shift().getId()).isEqualTo(5L);
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
    void noShiftThatDay_rejected() {
        when(shiftAssignmentRepository.findActiveByPtAndDate(PT_ID, DATE)).thenReturn(List.of());

        assertThatThrownBy(() -> validator.resolveSlot(PT_ID, BRANCH_ID, DATE, START, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không có ca làm việc");
    }

    /** Quyết định §4.3: giờ bắt đầu phải rơi đúng lưới slot, không phải giờ tự do. */
    @Test
    void startTimeOffTheSlotGrid_rejected() {
        assertThatThrownBy(() ->
                validator.resolveSlot(PT_ID, BRANCH_ID, DATE, LocalTime.of(18, 30), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không có ca làm việc");
    }

    /** Slot sau giờ kết thúc của ca không đặt được dù vẫn cùng ngày. */
    @Test
    void startTimeOutsideShift_rejected() {
        assertThatThrownBy(() ->
                validator.resolveSlot(PT_ID, BRANCH_ID, DATE, LocalTime.of(22, 0), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không có ca làm việc");
    }

    /** Ca của chi nhánh khác không phục vụ vé của chi nhánh này. */
    @Test
    void shiftOfAnotherBranch_rejected() {
        eveningShift.setGymBranch(GymBranch.builder().id(999L).name("Chi nhánh khác").build());

        assertThatThrownBy(() -> validator.resolveSlot(PT_ID, BRANCH_ID, DATE, START, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không có ca làm việc");
    }

    /** Điều kiện #4 mới: đơn nghỉ đã duyệt vô hiệu hoá slot dù ca vẫn còn đó. */
    @Test
    void approvedFullDayLeave_rejected() {
        when(leaveRequestRepository.findOverlapping(PT_ID, Set.of(LeaveStatus.APPROVED), DATE, DATE))
                .thenReturn(List.of(PtLeaveRequest.builder()
                        .id(7L).scope(LeaveScope.FULL_DAY).status(LeaveStatus.APPROVED)
                        .fromDate(DATE).toDate(DATE).build()));

        assertThatThrownBy(() -> validator.resolveSlot(PT_ID, BRANCH_ID, DATE, START, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("được duyệt nghỉ");
    }

    /** Nghỉ theo khoảng giờ chỉ chặn slot bị giao — slot khác trong ca vẫn đặt được. */
    @Test
    void approvedTimeRangeLeave_onlyBlocksOverlappingSlot() {
        when(leaveRequestRepository.findOverlapping(PT_ID, Set.of(LeaveStatus.APPROVED), DATE, DATE))
                .thenReturn(List.of(PtLeaveRequest.builder()
                        .id(8L).scope(LeaveScope.TIME_RANGE).status(LeaveStatus.APPROVED)
                        .fromDate(DATE).toDate(DATE)
                        .startTime(LocalTime.of(20, 0)).endTime(LocalTime.of(22, 0)).build()));

        assertThat(validator.resolveSlot(PT_ID, BRANCH_ID, DATE, START, null)).isNotNull();
        assertThatThrownBy(() ->
                validator.resolveSlot(PT_ID, BRANCH_ID, DATE, LocalTime.of(20, 0), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("được duyệt nghỉ");
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

        assertThat(validator.resolveSlot(PT_ID, BRANCH_ID, DATE, START, 99L)).isNotNull();
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
