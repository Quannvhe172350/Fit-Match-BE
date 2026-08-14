package com.fitmatch.service.impl;

import com.fitmatch.common.enums.PtStatus;
import com.fitmatch.common.enums.SessionStatus;
import com.fitmatch.common.enums.TicketStatus;
import com.fitmatch.entity.PtProfile;
import com.fitmatch.entity.Ticket;
import com.fitmatch.entity.TrainingSession;
import com.fitmatch.repository.PtAvailabilityRepository;
import com.fitmatch.repository.PtProfileRepository;
import com.fitmatch.repository.TicketRepository;
import com.fitmatch.repository.TrainingSessionRepository;
import com.fitmatch.service.SettlementService;
import com.fitmatch.service.TicketMaintenanceService;
import com.fitmatch.service.support.NotificationDispatcher;
import com.fitmatch.service.support.SessionLifecycle;
import com.fitmatch.service.support.TicketLifecycle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketMaintenanceServiceImpl implements TicketMaintenanceService {

    /** Nhắc trước khi vé hết hạn. */
    private static final int EXPIRY_WARNING_DAYS = 3;

    private final TicketRepository ticketRepository;
    private final TrainingSessionRepository sessionRepository;
    private final PtProfileRepository ptProfileRepository;
    private final PtAvailabilityRepository ptAvailabilityRepository;
    private final TicketLifecycle ticketLifecycle;
    private final SessionLifecycle sessionLifecycle;
    private final SettlementService settlementService;
    private final NotificationDispatcher notificationDispatcher;

    @Override
    @Transactional
    public int completeElapsedSessions() {
        List<TrainingSession> elapsed = sessionRepository
                .findByStatusAndSessionDateBefore(SessionStatus.SCHEDULED, LocalDate.now());
        if (elapsed.isEmpty()) {
            return 0;
        }
        // Gom vé lại: một vé gói 10 ngày sẽ có nhiều buổi cùng hoàn tất trong một
        // lượt chạy, không được kiểm tra USED_UP mười lần.
        Set<Ticket> touched = new LinkedHashSet<>();
        for (TrainingSession session : elapsed) {
            // Buổi tiêu theo NGÀY, không theo điểm danh (câu 9) — khách vắng mặt
            // hay có mặt đều DONE, và không còn NO_SHOW nào để phân biệt.
            sessionLifecycle.transition(session, SessionStatus.DONE, "Ngày tập đã trôi qua");
            sessionRepository.save(session);
            touched.add(session.getTicket());
        }
        for (Ticket ticket : touched) {
            markUsedUpIfComplete(ticket);
        }
        log.info("Completed {} elapsed session(s) across {} ticket(s)", elapsed.size(), touched.size());
        return elapsed.size();
    }

    /**
     * Vé chỉ USED_UP khi ĐÃ ĐẶT ĐỦ dayCount ngày và tất cả đã DONE. Vé còn ngày
     * chưa đặt thì vẫn ACTIVE — khách còn quyền dùng cho tới khi hết hạn, và
     * tiền chưa được giải ngân.
     */
    private void markUsedUpIfComplete(Ticket ticket) {
        if (ticket.getStatus() != TicketStatus.ACTIVE) {
            return;
        }
        List<TrainingSession> sessions = sessionRepository
                .findByTicket_IdOrderByDayIndexAsc(ticket.getId()).stream()
                .filter(s -> s.getStatus() != SessionStatus.CANCELLED)
                .toList();
        if (sessions.size() < ticket.getDayCount()) {
            return;
        }
        if (sessions.stream().anyMatch(s -> s.getStatus() != SessionStatus.DONE)) {
            return;
        }
        ticketLifecycle.transition(ticket, TicketStatus.USED_UP, "Đã dùng hết số ngày của vé");
        settlementService.settleTicketAfterFulfillment(ticket, "Ticket used up");
        ticketRepository.save(ticket);
        notificationDispatcher.ticketUsedUp(ticket);
    }

    @Override
    @Transactional
    public int expireOverdueTickets() {
        List<Ticket> overdue = ticketRepository
                .findByStatusAndExpiresAtBefore(TicketStatus.ACTIVE, LocalDateTime.now());
        for (Ticket ticket : overdue) {
            ticketLifecycle.transition(ticket, TicketStatus.EXPIRED, "Quá hạn sử dụng vé");
            // Câu 32: tiền tự về gym. settleTicketAfterFulfillment idempotent theo
            // settlementStatus nên chạy job hai lần không giải ngân hai lần.
            settlementService.settleTicketAfterFulfillment(ticket, "Ticket expired");
            ticketRepository.save(ticket);
            notificationDispatcher.ticketExpired(ticket);
        }
        if (!overdue.isEmpty()) {
            log.info("Expired {} ticket(s)", overdue.size());
        }
        return overdue.size();
    }

    @Override
    @Transactional(readOnly = true)
    public int notifyExpiringSoon() {
        // Cửa sổ hẹp đúng một ngày + job chạy mỗi ngày = mỗi vé được nhắc một
        // lần. Rẻ hơn nhiều so với thêm một cột cờ "đã nhắc" vào bảng vé.
        LocalDateTime from = LocalDateTime.now().plusDays(EXPIRY_WARNING_DAYS - 1L);
        LocalDateTime to = LocalDateTime.now().plusDays(EXPIRY_WARNING_DAYS);
        List<Ticket> expiring = ticketRepository
                .findByStatusAndExpiresAtBetween(TicketStatus.ACTIVE, from, to);
        for (Ticket ticket : expiring) {
            notificationDispatcher.ticketExpiringSoon(ticket, EXPIRY_WARNING_DAYS);
        }
        return expiring.size();
    }

    @Override
    @Transactional(readOnly = true)
    public int warnPtsWithThinAvailability() {
        int threshold = PtDailyAvailabilityServiceImpl.RECOMMENDED_DAYS;
        LocalDate today = LocalDate.now();
        int warned = 0;
        for (PtProfile pt : ptProfileRepository.findByStatus(PtStatus.ACTIVE)) {
            long days = ptAvailabilityRepository.countDistinctDaysFrom(pt.getId(), today);
            if (days >= threshold) {
                continue;
            }
            // Quyết định #8: CHỈ cảnh báo. PT này vẫn xuất hiện trong tìm kiếm và
            // khách vẫn đặt được — không có nhánh nào ẩn PT đi ở đây.
            notificationDispatcher.ptAvailabilityBelowThreshold(
                    pt.getUser(),
                    pt.getGymProfile() != null ? pt.getGymProfile().getUser() : null,
                    days, threshold);
            warned++;
        }
        return warned;
    }
}
