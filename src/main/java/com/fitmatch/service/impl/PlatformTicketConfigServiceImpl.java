package com.fitmatch.service.impl;

import com.fitmatch.common.AuditActions;
import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.dto.ticket.TicketExpiryConfigDto;
import com.fitmatch.entity.PlatformTicketConfig;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.repository.PlatformTicketConfigRepository;
import com.fitmatch.service.AuditService;
import com.fitmatch.service.PlatformTicketConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformTicketConfigServiceImpl implements PlatformTicketConfigService {

    private final PlatformTicketConfigRepository repository;
    private final AuditService auditService;

    @Override
    @Transactional(readOnly = true)
    public TicketExpiryConfigDto current() {
        return TicketExpiryConfigDto.of(require());
    }

    @Override
    @Transactional
    public TicketExpiryConfigDto update(String adminUsername, TicketExpiryConfigDto request) {
        PlatformTicketConfig config = require();
        String before = config.getDayTicketExpiryDays() + "/" + config.getPackageTicketExpiryDays();
        config.setDayTicketExpiryDays(request.getDayTicketExpiryDays());
        config.setPackageTicketExpiryDays(request.getPackageTicketExpiryDays());
        repository.save(config);

        auditService.record(AuditActions.SYSTEM_CONFIG_CHANGE, "PlatformTicketConfig", config.getId(),
                "Ticket expiry days " + before + " -> " + request.getDayTicketExpiryDays()
                        + "/" + request.getPackageTicketExpiryDays()
                        + " (chỉ áp cho vé bán từ nay; vé đã bán giữ hạn cũ)");
        log.info("Admin {} changed ticket expiry to {}/{} days", adminUsername,
                request.getDayTicketExpiryDays(), request.getPackageTicketExpiryDays());
        return TicketExpiryConfigDto.of(config);
    }

    private PlatformTicketConfig require() {
        return repository.findTopByOrderByIdDesc()
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR,
                        "Platform ticket config is missing (V67 seed)"));
    }
}
