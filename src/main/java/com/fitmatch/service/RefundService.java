package com.fitmatch.service;

import com.fitmatch.common.enums.RefundStatus;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.payment.RefundResponse;
import com.fitmatch.entity.Booking;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;

/**
 * Hoàn tiền / điều chỉnh thanh toán (UC-055/056). Quy ước: yêu cầu hoàn tiền
 * bao trùm TOÀN BỘ phần đang giữ của booking; khi duyệt một phần, phần còn lại
 * được coi là phí Gym giữ lại và chuyển sang pending settlement.
 */
public interface RefundService {

    /** UC-055 (Customer): mở yêu cầu hoàn tiền cho booking REJECTED/CANCELLED còn giữ tiền. */
    RefundResponse createForCustomer(String customerUsername, Long bookingId, String reason);

    /** UC-055 (Admin/Finance): mở yêu cầu thủ công cho toàn bộ phần đang giữ. */
    RefundResponse createByAdmin(Long bookingId, String reason, String actorUsername);

    /**
     * Tự động mở yêu cầu khi gym reject / hủy booking đã giữ tiền (UC-038/042).
     * Bỏ qua êm nếu không có tiền giữ hoặc đã có yêu cầu đang mở.
     */
    void autoCreate(Booking booking, String reason);

    /** UC-056: duyệt + thực thi (approvedAmount null = hoàn toàn bộ). */
    RefundResponse approveAndExecute(Long refundId, BigDecimal approvedAmount, String note, String actorUsername);

    /** UC-056: từ chối yêu cầu — tiền trở lại trạng thái HELD. */
    RefundResponse reject(Long refundId, String note, String actorUsername);

    PageResponse<RefundResponse> listForCustomer(String customerUsername, Pageable pageable);

    PageResponse<RefundResponse> listForAdmin(RefundStatus status, Pageable pageable);
}
