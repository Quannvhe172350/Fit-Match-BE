package com.fitmatch.service;

import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.admin.AuditLogResponse;
import com.fitmatch.entity.AuditLog;
import com.fitmatch.repository.AuditLogRepository;
import com.fitmatch.service.impl.AuditLogQueryServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogQueryServiceImplTest {

    @Mock private AuditLogRepository auditLogRepository;
    @InjectMocks private AuditLogQueryServiceImpl service;

    @Test
    @SuppressWarnings("unchecked")
    void search_mapsEntitiesToResponse() {
        AuditLog log = AuditLog.builder().id(1L).action("USER_LOCK").targetType("User")
                .targetId("2").description("x").build();
        Page<AuditLog> page = new PageImpl<>(List.of(log), PageRequest.of(0, 20), 1);
        when(auditLogRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);

        PageResponse<AuditLogResponse> res = service.search("USER_LOCK", null, null, null, null,
                PageRequest.of(0, 20));

        assertThat(res.getTotalElements()).isEqualTo(1);
        assertThat(res.getContent().get(0).getAction()).isEqualTo("USER_LOCK");
    }
}
