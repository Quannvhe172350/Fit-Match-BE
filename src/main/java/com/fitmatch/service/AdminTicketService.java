package com.fitmatch.service;

import com.fitmatch.common.enums.TicketStatus;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.ticket.TicketResponse;
import org.springframework.data.domain.Pageable;

/** Tra cứu vé cho Admin (hỗ trợ khách, đối soát, xử lý tranh chấp). */
public interface AdminTicketService {

    PageResponse<TicketResponse> search(TicketStatus status, Long gymProfileId,
                                        String customerUsername, Pageable pageable);

    TicketResponse detail(Long ticketId);
}
