package com.fitmatch.service.impl;

import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.LeaveScope;
import com.fitmatch.common.enums.LeaveStatus;
import com.fitmatch.common.enums.PtCancellationStatus;
import com.fitmatch.common.enums.SessionStatus;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.gym.GymLeavePolicyDto;
import com.fitmatch.dto.pt.PtLeaveRequestCreate;
import com.fitmatch.dto.pt.PtLeaveRequestResponse;
import com.fitmatch.entity.GymProfile;
import com.fitmatch.entity.GymShift;
import com.fitmatch.entity.PtLeaveRequest;
import com.fitmatch.entity.PtProfile;
import com.fitmatch.entity.PtShiftAssignment;
import com.fitmatch.entity.SessionPtCancellation;
import com.fitmatch.entity.Ticket;
import com.fitmatch.entity.TrainingSession;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.GymProfileRepository;
import com.fitmatch.repository.GymShiftRepository;
import com.fitmatch.repository.PtLeaveRequestRepository;
import com.fitmatch.repository.PtProfileRepository;
import com.fitmatch.repository.PtShiftAssignmentRepository;
import com.fitmatch.repository.SessionPtCancellationRepository;
import com.fitmatch.repository.TrainingSessionRepository;
import com.fitmatch.service.PtLeaveRequestService;
import com.fitmatch.service.SystemConfigService;
import com.fitmatch.service.support.NotificationDispatcher;
import com.fitmatch.service.support.PtDayRefundCalculator;
import com.fitmatch.service.support.SessionLifecycle;
import com.fitmatch.service.support.ShiftSlotResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Đơn xin nghỉ của PT — chỗ duy nhất PT tác động lên lịch của chính mình sau
 * khi Gym trở thành chủ lịch (V85).
 *
 * <p>Ba ràng buộc nghiệp vụ nặng nằm ở {@link #submit}:
 * <ol>
 *   <li>không chồng đơn đang hiệu lực;</li>
 *   <li>hạn mức đơn/tháng của Gym (quyết định §4.2, bật-tắt được);</li>
 *   <li>số giờ báo trước khi đơn đè lên buổi khách đã đặt (quyết định §4.1,
 *       Admin cấu hình qua {@code pt.leave.min-lead-hours}).</li>
 * </ol>
 *
 * <p>Và một luồng đặc biệt ở {@link #approve}: đơn phủ lên buổi SCHEDULED thì
 * KHÔNG bị chặn — PT bị gỡ khỏi buổi, buổi giữ nguyên trạng thái, và quyền
 * quyết định chuyển sang KHÁCH (đổi PT hay nhận hoàn phụ phí PT của ngày đó).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PtLeaveRequestServiceImpl implements PtLeaveRequestService {

    /** Dùng khi Admin chưa cấu hình / cấu hình sai định dạng. */
    private static final long DEFAULT_MIN_LEAD_HOURS = 48L;

    private static final String CONFIG_MIN_LEAD_HOURS = "pt.leave.min-lead-hours";

    /** Đơn còn hiệu lực — tiêu hạn mức và chặn đơn chồng lên. */
    private static final Set<LeaveStatus> EFFECTIVE =
            Set.of(LeaveStatus.PENDING, LeaveStatus.APPROVED);

    /** Trần khoảng ngày một đơn: nghỉ hơn một năm là nghỉ việc, không phải xin nghỉ. */
    private static final int MAX_LEAVE_DAYS = 366;

    private final PtLeaveRequestRepository leaveRepository;
    private final PtProfileRepository ptProfileRepository;
    private final GymProfileRepository gymProfileRepository;
    private final GymShiftRepository shiftRepository;
    private final PtShiftAssignmentRepository assignmentRepository;
    private final TrainingSessionRepository sessionRepository;
    private final SessionPtCancellationRepository cancellationRepository;
    private final SystemConfigService systemConfigService;
    private final ShiftSlotResolver slotResolver;
    private final PtDayRefundCalculator refundCalculator;
    private final SessionLifecycle sessionLifecycle;
    private final NotificationDispatcher notificationDispatcher;

    // ==================== Phía PT ====================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PtLeaveRequestResponse> myRequests(String ptUsername, Pageable pageable) {
        return PageResponse.of(
                leaveRepository.findByPtProfile_User_UsernameOrderByCreatedAtDesc(ptUsername, pageable),
                PtLeaveRequestResponse::of);
    }

    @Override
    @Transactional
    public PtLeaveRequestResponse submit(String ptUsername, PtLeaveRequestCreate request) {
        PtProfile pt = requirePt(ptUsername);
        GymProfile gym = pt.getGymProfile();
        if (gym == null) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "PT chưa thuộc phòng gym nào — không có ai duyệt đơn nghỉ");
        }

        List<String> reasons = new ArrayList<>();
        LocalDate from = request.getFromDate();
        LocalDate to = request.getToDate();
        if (to.isBefore(from)) {
            reasons.add("ngày kết thúc phải sau hoặc bằng ngày bắt đầu");
        } else if (from.datesUntil(to.plusDays(1)).count() > MAX_LEAVE_DAYS) {
            reasons.add("khoảng nghỉ tối đa " + MAX_LEAVE_DAYS + " ngày một đơn");
        }
        if (to.isBefore(LocalDate.now())) {
            reasons.add("không thể xin nghỉ cho ngày đã qua");
        }

        Set<GymShift> shifts = resolveShifts(pt, request, reasons);
        if (request.getScope() == LeaveScope.TIME_RANGE) {
            if (request.getStartTime() == null || request.getEndTime() == null) {
                reasons.add("phạm vi theo khoảng giờ phải có giờ bắt đầu và giờ kết thúc");
            } else if (!request.getStartTime().isBefore(request.getEndTime())) {
                reasons.add("giờ kết thúc phải sau giờ bắt đầu");
            }
        }
        if (!reasons.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Đơn nghỉ không hợp lệ: " + String.join("; ", reasons));
        }

        assertNoOverlappingRequest(pt, from, to);
        assertWithinMonthlyQuota(pt, gym, from);

        PtLeaveRequest leave = PtLeaveRequest.builder()
                .ptProfile(pt)
                .gymProfile(gym)
                .type(request.getType())
                .scope(request.getScope())
                .fromDate(from)
                .toDate(to)
                .startTime(request.getScope() == LeaveScope.TIME_RANGE ? request.getStartTime() : null)
                .endTime(request.getScope() == LeaveScope.TIME_RANGE ? request.getEndTime() : null)
                .shifts(shifts)
                .reason(request.getReason().trim())
                .attachmentUrl(request.getAttachmentUrl())
                .status(LeaveStatus.PENDING)
                .build();

        assertEnoughLeadTime(leave);

        PtLeaveRequest saved = leaveRepository.save(leave);
        notificationDispatcher.ptLeaveSubmitted(gym.getUser(), pt.getDisplayName(), saved);
        log.info("PT {} submitted leave {} [{}..{}] scope={} type={}",
                ptUsername, saved.getId(), from, to, saved.getScope(), saved.getType());
        return PtLeaveRequestResponse.of(saved);
    }

    @Override
    @Transactional
    public PtLeaveRequestResponse cancel(String ptUsername, Long requestId) {
        PtLeaveRequest leave = leaveRepository.findByIdAndPtProfile_User_Username(requestId, ptUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Leave request", requestId));
        if (leave.getStatus() != LeaveStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Chỉ huỷ được đơn đang chờ duyệt (hiện: " + leave.getStatus() + ")");
        }
        leave.setStatus(LeaveStatus.CANCELLED);
        leaveRepository.save(leave);
        log.info("PT {} cancelled leave {}", ptUsername, requestId);
        return PtLeaveRequestResponse.of(leave);
    }

    // ==================== Phía Gym ====================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PtLeaveRequestResponse> gymRequests(String gymUsername, LeaveStatus status,
                                                            Pageable pageable) {
        return PageResponse.of(status == null
                        ? leaveRepository.findByGymProfile_User_UsernameOrderByCreatedAtDesc(
                                gymUsername, pageable)
                        : leaveRepository.findByGymProfile_User_UsernameAndStatusOrderByCreatedAtDesc(
                                gymUsername, status, pageable),
                PtLeaveRequestResponse::of);
    }

    @Override
    @Transactional(readOnly = true)
    public long countPending(String gymUsername) {
        return leaveRepository.countByGymProfile_User_UsernameAndStatus(gymUsername, LeaveStatus.PENDING);
    }

    @Override
    @Transactional
    public PtLeaveRequestResponse approve(String gymUsername, Long requestId) {
        PtLeaveRequest leave = requirePendingOfGym(gymUsername, requestId);

        leave.setStatus(LeaveStatus.APPROVED);
        leave.setReviewedBy(gymUsername);
        leave.setReviewedAt(LocalDateTime.now());
        leaveRepository.saveAndFlush(leave);

        // Đọc buổi vướng SAU KHI ghi APPROVED (edge case §7.9b): lượt đặt lọt
        // vào giữa hai thao tác vẫn bị bắt ở đây, vì PtSlotValidator của nó
        // hoặc chạy trước khi đơn được duyệt (rồi bị lần đọc này bắt), hoặc
        // chạy sau (và tự bị đơn APPROVED từ chối).
        List<TrainingSession> affected = affectedSessions(leave);
        for (TrainingSession session : affected) {
            detachPtAndAskCustomer(leave, session);
        }

        notificationDispatcher.ptLeaveApproved(
                leave.getPtProfile().getUser(), leave, affected.size());
        log.info("Gym {} approved leave {} — {} session(s) handed back to customers",
                gymUsername, requestId, affected.size());
        return PtLeaveRequestResponse.of(leave);
    }

    @Override
    @Transactional
    public PtLeaveRequestResponse reject(String gymUsername, Long requestId, String reason) {
        PtLeaveRequest leave = requirePendingOfGym(gymUsername, requestId);
        leave.setStatus(LeaveStatus.REJECTED);
        leave.setRejectReason(reason);
        leave.setReviewedBy(gymUsername);
        leave.setReviewedAt(LocalDateTime.now());
        leaveRepository.save(leave);

        notificationDispatcher.ptLeaveRejected(leave.getPtProfile().getUser(), leave, reason);
        log.info("Gym {} rejected leave {}", gymUsername, requestId);
        return PtLeaveRequestResponse.of(leave);
    }

    @Override
    @Transactional(readOnly = true)
    public GymLeavePolicyDto getPolicy(String gymUsername) {
        GymProfile gym = requireGym(gymUsername);
        return GymLeavePolicyDto.builder()
                .enabled(gym.isLeaveQuotaEnabled())
                .monthlyQuota(gym.getLeaveMonthlyQuota())
                .build();
    }

    @Override
    @Transactional
    public GymLeavePolicyDto updatePolicy(String gymUsername, GymLeavePolicyDto policy) {
        GymProfile gym = requireGym(gymUsername);
        boolean enabled = Boolean.TRUE.equals(policy.getEnabled());
        if (enabled && policy.getMonthlyQuota() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "monthlyQuota is required when the quota is enabled");
        }
        gym.setLeaveQuotaEnabled(enabled);
        gym.setLeaveMonthlyQuota(enabled ? policy.getMonthlyQuota() : null);
        gymProfileRepository.save(gym);
        log.info("Gym {} set leave quota: enabled={}, quota={}",
                gymUsername, enabled, gym.getLeaveMonthlyQuota());
        return GymLeavePolicyDto.builder()
                .enabled(gym.isLeaveQuotaEnabled())
                .monthlyQuota(gym.getLeaveMonthlyQuota())
                .build();
    }

    // ==================== Ràng buộc ====================

    private void assertNoOverlappingRequest(PtProfile pt, LocalDate from, LocalDate to) {
        List<PtLeaveRequest> overlapping =
                leaveRepository.findOverlapping(pt.getId(), EFFECTIVE, from, to);
        if (!overlapping.isEmpty()) {
            String detail = overlapping.stream()
                    .map(r -> "#" + r.getId() + " (" + r.getFromDate() + " - " + r.getToDate()
                            + ", " + r.getStatus() + ")")
                    .reduce((a, b) -> a + ", " + b).orElse("");
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Đã có đơn nghỉ chồng lấn khoảng ngày này: " + detail
                            + ". Huỷ đơn cũ trước khi gửi đơn mới.");
        }
    }

    /**
     * Quyết định §4.2. Đếm theo THÁNG của {@code fromDate}: một đơn vắt qua hai
     * tháng vẫn tính một lượt vào tháng bắt đầu — đếm cả hai tháng sẽ phạt PT
     * hai lần cho cùng một lần nghỉ.
     */
    private void assertWithinMonthlyQuota(PtProfile pt, GymProfile gym, LocalDate from) {
        if (!gym.isLeaveQuotaEnabled() || gym.getLeaveMonthlyQuota() == null) {
            return;
        }
        LocalDate monthStart = from.withDayOfMonth(1);
        LocalDate monthEnd = monthStart.plusMonths(1).minusDays(1);
        long used = leaveRepository.countInMonth(pt.getId(), EFFECTIVE, monthStart, monthEnd);
        if (used >= gym.getLeaveMonthlyQuota()) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Đã dùng hết " + used + "/" + gym.getLeaveMonthlyQuota()
                            + " lượt nghỉ của tháng " + monthStart.getMonthValue() + "/"
                            + monthStart.getYear() + ". Liên hệ phòng gym nếu có việc đột xuất.");
        }
    }

    /**
     * Quyết định §4.1: đơn đè lên buổi khách đã đặt phải được nộp trước N giờ,
     * N do Admin cấu hình. Chặn CỨNG — PT báo muộn thì phòng gym xử lý ngoài hệ
     * thống, hệ thống không nhận đơn để tránh khoá slot sát giờ khiến khách
     * không kịp tìm PT khác.
     *
     * <p>Đơn không đè lên buổi nào thì không áp ràng buộc này: PT được báo bận
     * cho khung giờ trống bất cứ lúc nào.
     */
    private void assertEnoughLeadTime(PtLeaveRequest leave) {
        List<TrainingSession> affected = affectedSessions(leave);
        if (affected.isEmpty()) {
            return;
        }
        long minLeadHours = minLeadHours();
        LocalDateTime now = LocalDateTime.now();

        TrainingSession earliest = null;
        LocalDateTime earliestStart = null;
        for (TrainingSession session : affected) {
            LocalDateTime start = LocalDateTime.of(session.getSessionDate(), session.getPtSlotStart());
            if (earliestStart == null || start.isBefore(earliestStart)) {
                earliestStart = start;
                earliest = session;
            }
        }
        long hoursLeft = ChronoUnit.HOURS.between(now, earliestStart);
        if (hoursLeft < minLeadHours) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Buổi tập ngày " + earliest.getSessionDate() + " lúc " + earliest.getPtSlotStart()
                            + " chỉ còn " + Math.max(hoursLeft, 0) + " giờ, trong khi quy định là báo"
                            + " trước ít nhất " + minLeadHours + " giờ. Liên hệ phòng gym để xử lý"
                            + " trường hợp đột xuất.");
        }
    }

    private long minLeadHours() {
        Long configured = systemConfigService.findLong(CONFIG_MIN_LEAD_HOURS);
        return configured == null || configured < 0 ? DEFAULT_MIN_LEAD_HOURS : configured;
    }

    // ==================== Buổi bị ảnh hưởng ====================

    /**
     * Buổi SCHEDULED của PT nằm trong phạm vi đơn. Duyệt theo NGÀY rồi đối chiếu
     * từng ca vì {@code scope} có thể chỉ phủ một phần ngày.
     *
     * <p>Chỉ SCHEDULED: buổi DONE đã diễn ra và buổi CANCELLED đã đóng, không
     * được đụng tới (edge case §7.10).
     */
    private List<TrainingSession> affectedSessions(PtLeaveRequest leave) {
        Long ptId = leave.getPtProfile().getId();
        LocalDate from = leave.getFromDate().isBefore(LocalDate.now())
                ? LocalDate.now() : leave.getFromDate();
        List<TrainingSession> affected = new ArrayList<>();

        for (LocalDate date = from; !date.isAfter(leave.getToDate()); date = date.plusDays(1)) {
            List<TrainingSession> sessions = sessionRepository
                    .findByPtProfile_IdAndSessionDateAndStatusIn(
                            ptId, date, List.of(SessionStatus.SCHEDULED));
            if (sessions.isEmpty()) {
                continue;
            }
            List<PtShiftAssignment> shiftsOfDay = assignmentRepository.findActiveByPtAndDate(ptId, date);
            for (TrainingSession session : sessions) {
                if (session.getPtSlotStart() == null) {
                    continue;
                }
                GymShift shift = shiftOf(shiftsOfDay, session);
                if (slotResolver.leaveCovers(leave, date, session.getPtSlotStart(),
                        session.getPtSlotEnd() != null
                                ? session.getPtSlotEnd() : session.getPtSlotStart().plusMinutes(1),
                        shift)) {
                    affected.add(session);
                }
            }
        }
        return affected;
    }

    /** Ca chứa khung giờ của buổi — null khi buổi nằm ngoài mọi ca hiện có. */
    private GymShift shiftOf(List<PtShiftAssignment> shiftsOfDay, TrainingSession session) {
        for (PtShiftAssignment assignment : shiftsOfDay) {
            GymShift shift = assignment.getGymShift();
            if (!session.getPtSlotStart().isBefore(shift.getStartTime())
                    && session.getPtSlotStart().isBefore(shift.getEndTime())) {
                return shift;
            }
        }
        return null;
    }

    /**
     * Quyết định §4.1: gỡ PT khỏi buổi và chuyển quyền quyết định cho khách.
     *
     * <p>Buổi KHÔNG đổi trạng thái và KHÔNG bị huỷ — vé có giá trị cả ngày, mất
     * PT không phải mất quyền vào tập. Đây cũng chính là quy ước sẵn có của
     * {@code SessionLifecycle}: thêm/bỏ PT ghi {@code recordNote}, không transition.
     */
    private void detachPtAndAskCustomer(PtLeaveRequest leave, TrainingSession session) {
        // Buổi đã có một quyết định treo (PT thay thế cũng xin nghỉ) thì đóng
        // dòng cũ lại trước, tránh hai dòng PENDING cùng trỏ vào một buổi.
        cancellationRepository
                .findFirstByTrainingSession_IdAndStatusOrderByIdDesc(
                        session.getId(), PtCancellationStatus.PENDING_CUSTOMER)
                .ifPresent(open -> {
                    open.setStatus(PtCancellationStatus.REPLACED);
                    open.setResolvedAt(LocalDateTime.now());
                    cancellationRepository.save(open);
                });

        PtProfile formerPt = session.getPtProfile();
        java.time.LocalTime formerSlotStart = session.getPtSlotStart();
        cancellationRepository.save(SessionPtCancellation.builder()
                .trainingSession(session)
                .leaveRequest(leave)
                .formerPtProfile(formerPt)
                .formerSlotStart(session.getPtSlotStart())
                .formerSlotEnd(session.getPtSlotEnd())
                .status(PtCancellationStatus.PENDING_CUSTOMER)
                .build());

        session.setPtProfile(null);
        session.setPtSlotStart(null);
        session.setPtSlotEnd(null);
        sessionLifecycle.recordNote(session,
                "PT #" + formerPt.getId() + " nghỉ theo đơn #" + leave.getId()
                        + " — chờ khách chọn PT thay thế hoặc nhận hoàn phụ phí");
        sessionRepository.save(session);

        Ticket ticket = session.getTicket();
        BigDecimal estimated = refundCalculator.refundForOneDay(ticket);
        notificationDispatcher.sessionPtCancelled(
                session, formerPt.getDisplayName(), formerSlotStart, estimated);
    }

    // ==================== helpers ====================

    private Set<GymShift> resolveShifts(PtProfile pt, PtLeaveRequestCreate request,
                                        List<String> reasons) {
        if (request.getScope() != LeaveScope.SHIFT) {
            return new LinkedHashSet<>();
        }
        if (request.getShiftIds() == null || request.getShiftIds().isEmpty()) {
            reasons.add("phạm vi theo ca phải chọn ít nhất một ca");
            return new LinkedHashSet<>();
        }
        // Chỉ chấp nhận ca thuộc chi nhánh PT được phân công — nếu không PT của
        // Gym A có thể xin nghỉ một ca của Gym B.
        Set<Long> allowed = new LinkedHashSet<>();
        for (GymShift shift : shiftRepository.findActiveShiftsOfPtBranches(pt.getId())) {
            allowed.add(shift.getId());
        }
        Set<GymShift> shifts = new LinkedHashSet<>();
        for (Long shiftId : request.getShiftIds()) {
            if (!allowed.contains(shiftId)) {
                reasons.add("ca #" + shiftId + " không thuộc chi nhánh bạn phụ trách");
                continue;
            }
            shiftRepository.findById(shiftId).ifPresent(shifts::add);
        }
        return shifts;
    }

    private PtLeaveRequest requirePendingOfGym(String gymUsername, Long requestId) {
        PtLeaveRequest leave = leaveRepository
                .findByIdAndGymProfile_User_Username(requestId, gymUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Leave request", requestId));
        if (leave.getStatus() != LeaveStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Đơn không còn ở trạng thái chờ duyệt (hiện: " + leave.getStatus() + ")");
        }
        return leave;
    }

    private PtProfile requirePt(String username) {
        return ptProfileRepository.findByUser_Username(username)
                .orElseThrow(() -> new ResourceNotFoundException("PT profile for user", username));
    }

    private GymProfile requireGym(String username) {
        return gymProfileRepository.findByUser_Username(username)
                .orElseThrow(() -> new ResourceNotFoundException("Gym profile for user", username));
    }
}
