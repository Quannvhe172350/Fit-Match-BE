package com.fitmatch.service;

import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.voucher.VoucherRequest;
import com.fitmatch.dto.voucher.VoucherResponse;
import com.fitmatch.entity.Ticket;
import com.fitmatch.entity.Voucher;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;

/**
 * Voucher/khuyến mãi (UC-073). Admin cấu hình; khách gõ mã ở bước MUA VÉ; hệ
 * thống chốt số giảm và tiêu thụ lượt ngay khi mua.
 */
public interface VoucherService {

    // ----- Admin -----
    VoucherResponse create(VoucherRequest request);

    VoucherResponse update(Long id, VoucherRequest request);

    VoucherResponse setActive(Long id, boolean active);

    PageResponse<VoucherResponse> list(Pageable pageable);

    // ----- Dùng nội bộ khi mua vé -----

    /**
     * Tra mã và kiểm tra còn hiệu lực (active, trong hạn, chưa vượt usageLimit).
     * Ném BusinessException nếu không dùng được.
     */
    Voucher requireUsable(String code);

    /** Số tiền giảm cho voucher trên một tổng giá trị (0 nếu không đủ điều kiện). */
    BigDecimal computeDiscount(Voucher voucher, BigDecimal total);

    /** Tiêu thụ một lượt voucher khi mua vé (khoá + tăng usedCount). */
    void consumeForTicket(Ticket ticket);

    /**
     * Trả lại một lượt voucher khi vé bị huỷ hoặc hoàn toàn bộ. Không ném lỗi
     * vào luồng chính; idempotency do caller giữ bằng cờ {@code promoReleased}.
     */
    void releaseFromTicket(Ticket ticket);
}
