package com.fitmatch.service;

import com.fitmatch.common.enums.RefundStatus;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.payment.RefundResponse;
import com.fitmatch.dto.ticket.ApproveTicketRefundRequest;
import com.fitmatch.dto.ticket.TicketRefundPreviewResponse;
import org.springframework.data.domain.Pageable;

/**
 * Hoàn tiền vé (câu 4 + 11 + 12 + 13 + 32).
 *
 * <p>Mọi yêu cầu đều qua Admin — không có nhánh tự duyệt. Khách tạo yêu cầu,
 * admin xem preview hai lựa chọn rồi chọn FULL hoặc PARTIAL_ELAPSED; số tiền do
 * {@code PartialRefundCalculator} quyết chứ không do admin gõ tay.
 */
public interface TicketRefundService {

    /** Khách mở yêu cầu hoàn cho một vé đang dùng được. */
    RefundResponse requestByCustomer(String customerUsername, Long ticketId, String reason);

    PageResponse<RefundResponse> listForCustomer(String customerUsername, Pageable pageable);

    PageResponse<RefundResponse> listForAdmin(RefundStatus status, Pageable pageable);

    /** Màn quyết định của admin: đã trả bao nhiêu, đã qua mấy ngày, hai mức hoàn. */
    TicketRefundPreviewResponse preview(Long refundRequestId);

    /**
     * Duyệt và thực thi. Huỷ toàn bộ buổi tập tương lai của vé (câu 12) và
     * chuyển phần giữ lại sang pending settlement cho gym.
     */
    RefundResponse approve(Long refundRequestId, ApproveTicketRefundRequest request, String actorUsername);

    /** Từ chối — tiền quay lại HELD, khách được báo và có thể mở tranh chấp. */
    RefundResponse reject(Long refundRequestId, String note, String actorUsername);
}
