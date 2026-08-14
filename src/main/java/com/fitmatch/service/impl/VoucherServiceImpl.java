package com.fitmatch.service.impl;

import com.fitmatch.common.enums.DiscountType;
import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.voucher.VoucherRequest;
import com.fitmatch.dto.voucher.VoucherResponse;
import com.fitmatch.entity.Voucher;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.VoucherRepository;
import com.fitmatch.service.VoucherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoucherServiceImpl implements VoucherService {

    private final VoucherRepository voucherRepository;

    @Override
    @Transactional
    public VoucherResponse create(VoucherRequest request) {
        if (voucherRepository.existsByCodeIgnoreCase(request.getCode())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Voucher code already exists");
        }
        validateConfig(request);
        Voucher v = Voucher.builder()
                .code(request.getCode().trim())
                .description(request.getDescription())
                .discountType(request.getDiscountType())
                .discountValue(request.getDiscountValue())
                .minBookingAmount(request.getMinBookingAmount())
                .maxDiscount(request.getMaxDiscount())
                .usageLimit(request.getUsageLimit())
                .validFrom(request.getValidFrom())
                .validTo(request.getValidTo())
                .active(request.getActive() == null || request.getActive())
                .build();
        return VoucherResponse.of(voucherRepository.save(v));
    }

    @Override
    @Transactional
    public VoucherResponse update(Long id, VoucherRequest request) {
        Voucher v = require(id);
        validateConfig(request);
        // Không cho đổi code sang code đã tồn tại của voucher khác.
        if (!v.getCode().equalsIgnoreCase(request.getCode())
                && voucherRepository.existsByCodeIgnoreCase(request.getCode())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Voucher code already exists");
        }
        v.setCode(request.getCode().trim());
        v.setDescription(request.getDescription());
        v.setDiscountType(request.getDiscountType());
        v.setDiscountValue(request.getDiscountValue());
        v.setMinBookingAmount(request.getMinBookingAmount());
        v.setMaxDiscount(request.getMaxDiscount());
        v.setUsageLimit(request.getUsageLimit());
        v.setValidFrom(request.getValidFrom());
        v.setValidTo(request.getValidTo());
        if (request.getActive() != null) v.setActive(request.getActive());
        return VoucherResponse.of(v);
    }

    @Override
    @Transactional
    public VoucherResponse setActive(Long id, boolean active) {
        Voucher v = require(id);
        v.setActive(active);
        return VoucherResponse.of(v);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<VoucherResponse> list(Pageable pageable) {
        return PageResponse.of(voucherRepository.findAllByOrderByIdDesc(pageable), VoucherResponse::of);
    }


    @Override
    public BigDecimal computeDiscount(Voucher voucher, BigDecimal total) {
        if (total == null || total.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        if (voucher.getMinBookingAmount() != null
                && total.compareTo(voucher.getMinBookingAmount()) < 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal discount = voucher.getDiscountType() == DiscountType.PERCENT
                ? total.multiply(voucher.getDiscountValue())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : voucher.getDiscountValue();
        if (voucher.getMaxDiscount() != null && discount.compareTo(voucher.getMaxDiscount()) > 0) {
            discount = voucher.getMaxDiscount();
        }
        // Không giảm quá tổng giá trị.
        return discount.min(total);
    }

    // ---------- mô hình vé ----------

    @Override
    @Transactional(readOnly = true)
    public Voucher requireUsable(String code) {
        Voucher voucher = voucherRepository.findByCodeIgnoreCase(code.trim())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Voucher not found"));
        assertUsable(voucher);
        return voucher;
    }

    @Override
    @Transactional
    public void consumeForTicket(com.fitmatch.entity.Ticket ticket) {
        if (ticket.getVoucher() == null) return;
        Voucher v = voucherRepository.lockById(ticket.getVoucher().getId()).orElseThrow();
        assertUsable(v);
        v.setUsedCount(v.getUsedCount() + 1);
        voucherRepository.save(v);
    }

    @Override
    @Transactional
    public void releaseFromTicket(com.fitmatch.entity.Ticket ticket) {
        try {
            if (ticket.getVoucher() == null) return;
            Voucher v = voucherRepository.lockById(ticket.getVoucher().getId()).orElseThrow();
            if (v.getUsedCount() > 0) {
                v.setUsedCount(v.getUsedCount() - 1);
                voucherRepository.save(v);
                log.info("Voucher {} usage released for cancelled ticket {}", v.getCode(), ticket.getId());
            }
        } catch (Exception e) {
            log.warn("Voucher release failed for ticket {}: {}", ticket.getId(), e.getMessage());
        }
    }

    // ---------- helpers ----------

    private void assertUsable(Voucher v) {
        LocalDateTime now = LocalDateTime.now();
        if (!v.isActive()) throw new BusinessException(ErrorCode.INVALID_STATE, "Voucher is inactive");
        if (v.getValidFrom() != null && now.isBefore(v.getValidFrom())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Voucher is not yet valid");
        }
        if (v.getValidTo() != null && now.isAfter(v.getValidTo())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Voucher has expired");
        }
        if (v.getUsageLimit() != null && v.getUsedCount() >= v.getUsageLimit()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Voucher usage limit reached");
        }
    }

    private void validateConfig(VoucherRequest r) {
        if (r.getDiscountType() == DiscountType.PERCENT
                && r.getDiscountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "PERCENT discountValue must be <= 100");
        }
        if (r.getValidFrom() != null && r.getValidTo() != null
                && r.getValidTo().isBefore(r.getValidFrom())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "validTo must be after validFrom");
        }
    }

    private Voucher require(Long id) {
        return voucherRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Voucher", id));
    }
}
