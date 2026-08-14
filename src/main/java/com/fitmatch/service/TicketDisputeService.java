package com.fitmatch.service;

import com.fitmatch.dto.dispute.DisputeResponse;

/**
 * Câu 34: tranh chấp neo được cả vé lẫn buổi tập.
 *
 * <ul>
 *   <li><b>Cấp vé</b> (sessionId = null): đóng băng TOÀN BỘ phần đang giữ của
 *       vé. Dùng khi cả vé có vấn đề — gym đóng cửa, sai loại vé...</li>
 *   <li><b>Cấp buổi</b>: chỉ đóng băng giá trị MỘT ngày tập
 *       ({@code payableAmount / dayCount}). Dùng cho sự cố một buổi — PT không
 *       đến chẳng hạn — để phần còn lại của vé vẫn chạy bình thường.</li>
 * </ul>
 *
 * Việc giải quyết dùng chung luồng moderator hiện có
 * ({@code DisputeService.resolve}); phần tiền do
 * {@code DisputeFinancialApplier} xử lý theo neo.
 */
public interface TicketDisputeService {

    /**
     * @param sessionId null = tranh chấp cấp vé; khác null = tranh chấp cấp buổi
     */
    DisputeResponse open(String username, Long ticketId, Long sessionId, String reason);
}
