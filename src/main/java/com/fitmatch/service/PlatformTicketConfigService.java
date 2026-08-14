package com.fitmatch.service;

import com.fitmatch.dto.ticket.TicketExpiryConfigDto;

/** Câu 32: Admin cấu hình hạn dùng vé (mẫu "một dòng hiệu lực"). */
public interface PlatformTicketConfigService {

    TicketExpiryConfigDto current();

    TicketExpiryConfigDto update(String adminUsername, TicketExpiryConfigDto request);
}
