package com.fitmatch.service.impl;

import com.fitmatch.common.enums.BookingStatus;
import com.fitmatch.common.enums.DiscountType;
import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.booking.BookingResponse;
import com.fitmatch.dto.voucher.VoucherRequest;
import com.fitmatch.dto.voucher.VoucherResponse;
import com.fitmatch.entity.Booking;
import com.fitmatch.entity.Voucher;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.BookingRepository;
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
    private final BookingRepository bookingRepository;

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
    @Transactional
    public BookingResponse applyToBooking(String customerUsername, Long bookingId, String code) {
        Booking booking = requireDraft(customerUsername, bookingId);
        Voucher voucher = voucherRepository.findByCodeIgnoreCase(code.trim())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Voucher not found"));
        assertUsable(voucher);
        BigDecimal total = bookingTotal(booking);
        BigDecimal discount = computeDiscount(voucher, total);
        if (discount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Voucher does not apply to this booking (min amount not met or no discount)");
        }
        // UC-073: voucher và điểm thưởng loại trừ lẫn nhau — áp voucher thì gỡ điểm.
        booking.setLoyaltyPointsUsed(null);
        booking.setVoucher(voucher);
        booking.setDiscountAmount(discount);
        bookingRepository.save(booking);
        log.info("Voucher {} applied to booking {} (discount {})", voucher.getCode(), bookingId, discount);
        return BookingResponse.of(booking);
    }

    @Override
    @Transactional
    public BookingResponse removeFromBooking(String customerUsername, Long bookingId) {
        Booking booking = requireDraft(customerUsername, bookingId);
        booking.setVoucher(null);
        booking.setDiscountAmount(null);
        bookingRepository.save(booking);
        return BookingResponse.of(booking);
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

    @Override
    @Transactional
    public void recomputeDiscount(Booking booking) {
        if (booking.getVoucher() == null) {
            booking.setDiscountAmount(null);
            return;
        }
        // Nếu voucher hết hiệu lực khi checkout -> bỏ áp dụng (không chặn booking).
        try {
            assertUsable(booking.getVoucher());
        } catch (BusinessException e) {
            log.info("Voucher {} no longer usable at checkout for booking {} - dropped",
                    booking.getVoucher().getCode(), booking.getId());
            booking.setVoucher(null);
            booking.setDiscountAmount(null);
            return;
        }
        booking.setDiscountAmount(computeDiscount(booking.getVoucher(), bookingTotal(booking)));
    }

    @Override
    @Transactional
    public void consumeAtCheckout(Booking booking) {
        if (booking.getVoucher() == null) return;
        Voucher v = voucherRepository.lockById(booking.getVoucher().getId()).orElseThrow();
        assertUsable(v);
        v.setUsedCount(v.getUsedCount() + 1);
        voucherRepository.save(v);
    }

    @Override
    @Transactional
    public void releaseFromBooking(Booking booking) {
        try {
            if (booking.getVoucher() == null) return;
            Voucher v = voucherRepository.lockById(booking.getVoucher().getId()).orElseThrow();
            if (v.getUsedCount() > 0) {
                v.setUsedCount(v.getUsedCount() - 1);
                voucherRepository.save(v);
                log.info("Voucher {} usage released for cancelled booking {}", v.getCode(), booking.getId());
            }
        } catch (Exception e) {
            log.warn("Voucher release failed for booking {}: {}", booking.getId(), e.getMessage());
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

    private BigDecimal bookingTotal(Booking b) {
        if (b.getGymService() != null) return b.getGymService().getPrice();
        if (b.getTrainingPackage() != null && b.getCustomerPackage() == null) {
            return b.getTrainingPackage().getPrice();
        }
        return BigDecimal.ZERO; // buổi từ gói đã mua: miễn phí, voucher không áp.
    }

    private Booking requireDraft(String customerUsername, Long bookingId) {
        Booking booking = bookingRepository.findByIdAndCustomer_Username(bookingId, customerUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
        if (booking.getStatus() != BookingStatus.DRAFT) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Voucher can only be applied to a DRAFT booking");
        }
        if (booking.getCustomerPackage() != null) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Voucher does not apply to a free package session");
        }
        return booking;
    }

    private Voucher require(Long id) {
        return voucherRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Voucher", id));
    }
}
