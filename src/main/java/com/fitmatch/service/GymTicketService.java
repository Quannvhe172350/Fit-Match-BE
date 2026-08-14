package com.fitmatch.service;

import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.ticket.ConfirmPtSessionRequest;
import com.fitmatch.dto.ticket.GymCalendarDayResponse;
import com.fitmatch.dto.ticket.TicketResponse;
import com.fitmatch.dto.ticket.TrainingSessionResponse;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

/**
 * Phía Gym trong mô hình vé: xem lịch, xem vé đã bán, xác nhận buổi có PT.
 * Cố ý KHÔNG có duyệt / từ chối / gán PT / dời lịch / huỷ — gym không can thiệp
 * vào lịch của khách nữa (quyết định #7).
 */
public interface GymTicketService {

    /** Lịch đã gom theo ngày cho đúng khoảng đang xem. */
    List<GymCalendarDayResponse> calendar(String gymUsername, Long branchId, LocalDate from, LocalDate to);

    /** Vé đã bán — để gym theo dõi doanh thu và biết khách mua gì. */
    PageResponse<TicketResponse> tickets(String gymUsername, Long branchId,
                                         LocalDate from, LocalDate to, Pageable pageable);

    /** Câu 31/33: xác nhận PT có đến kèm ảnh. Không chặn tiền. */
    TrainingSessionResponse confirmPtSession(String gymUsername, Long sessionId,
                                             ConfirmPtSessionRequest request);
}
