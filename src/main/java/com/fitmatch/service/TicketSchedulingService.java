package com.fitmatch.service;

import com.fitmatch.dto.ticket.ScheduleTicketRequest;
import com.fitmatch.dto.ticket.TicketResponse;
import com.fitmatch.dto.ticket.TrainingSessionResponse;
import com.fitmatch.dto.ticket.UpdateSessionPtRequest;

import java.time.LocalDate;
import java.util.List;

/** Đặt lịch và chỉnh sửa ngày tập của một vé đã kích hoạt. */
public interface TicketSchedulingService {

    /**
     * Đặt lịch cho vé. Vé DAY: một ngày. Vé PACKAGE: server sinh đủ dayCount
     * ngày liên tiếp từ startDate (câu 27); PT gán cho từng ngày là tuỳ chọn,
     * ngày để trống bổ sung sau (câu 8).
     */
    TicketResponse schedule(String customerUsername, Long ticketId, ScheduleTicketRequest request);

    /** Lịch tập của khách trong một khoảng ngày — dùng cho cảnh báo trùng ngày (câu 29). */
    List<TrainingSessionResponse> mySessions(String customerUsername, LocalDate from, LocalDate to);

    /** Dời ngày — cả vé DAY lẫn vé gói (V94 nới; xem SessionSchedulingValidator). */
    TrainingSessionResponse updateDate(String customerUsername, Long sessionId, LocalDate date);

    /** Đổi khung giờ PT trong cùng ngày, hoặc bổ sung PT cho ngày đang trống (câu 34). */
    TrainingSessionResponse setPt(String customerUsername, Long sessionId, UpdateSessionPtRequest request);

    /** Bỏ PT khỏi một ngày. Không hoàn tiền — phụ phí PT tính theo vé, không theo ngày. */
    TrainingSessionResponse removePt(String customerUsername, Long sessionId);

    /** Khách check-in — chỉ buổi có PT. Không đổi trạng thái, không đụng dòng tiền. */
    TrainingSessionResponse checkIn(String customerUsername, Long sessionId);

    /**
     * V94: huỷ buổi này ngay bây giờ thì được hoàn bao nhiêu — hỏi TRƯỚC khi huỷ.
     *
     * <p>Không ném lỗi khi buổi không huỷ được: trả {@code cancellable = false}
     * kèm lý do, vì FE cần hiện nút ở trạng thái mờ có giải thích chứ không phải
     * một lỗi đỏ cho một câu hỏi hoàn toàn hợp lệ.
     */
    com.fitmatch.dto.ticket.SessionCancellationQuote cancelQuote(String customerUsername, Long sessionId);

    /**
     * V94: khách huỷ một ngày tập. Hoàn theo mốc báo trước của gym, tiền vào ví
     * khách, và ngày đó KHÔNG được trả lại cho vé (khách đã cầm tiền của nó).
     */
    com.fitmatch.dto.ticket.SessionCancellationQuote cancel(String customerUsername, Long sessionId,
                                                            String reason);
}
