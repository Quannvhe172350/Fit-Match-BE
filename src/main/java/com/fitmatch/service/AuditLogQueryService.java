package com.fitmatch.service;

import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.admin.AuditLogResponse;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;

public interface AuditLogQueryService {

    /** UC-13: xem/lọc audit logs (action, targetType, actor, khoảng thời gian) có phân trang. */
    PageResponse<AuditLogResponse> search(String action, String targetType, String actor,
                                          LocalDateTime from, LocalDateTime to, Pageable pageable);
}
