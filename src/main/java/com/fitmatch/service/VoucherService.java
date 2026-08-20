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
     *
     * <p>CHỈ dùng ở luồng MUA (/tickets/purchase), nơi mã sai phải chặn giao dịch.
     * Màn BÁO GIÁ phải dùng {@link #checkUsable(String)} — xem lý do ở đó.
     */
    Voucher requireUsable(String code);

    /**
     * Kết quả tra voucher không-ném-lỗi: {@code voucher} null nghĩa là không dùng
     * được, khi đó {@code message} là lý do để hiển thị dưới ô nhập mã.
     */
    record UsableCheck(Voucher voucher, String message) {
    }

    /**
     * Bản không ném lỗi của {@link #requireUsable(String)}, dành cho màn BÁO GIÁ.
     *
     * <p>Vì sao không để caller bắt {@code BusinessException} của
     * {@code requireUsable}: hai phương thức đều {@code @Transactional} nên lời gọi
     * đi qua proxy và tham gia CHUNG transaction của caller. Ngoại lệ ném ra từ
     * phương thức bên trong khiến Spring đánh dấu transaction dùng chung là
     * rollback-only; caller nuốt ngoại lệ rồi chạy tiếp bình thường, nhưng đến lúc
     * commit thì vỡ ra {@code UnexpectedRollbackException} → 500. Cách duy nhất an
     * toàn là KHÔNG để ngoại lệ vượt qua ranh giới transaction.
     */
    UsableCheck checkUsable(String code);

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
