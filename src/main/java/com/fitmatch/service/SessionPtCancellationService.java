package com.fitmatch.service;

import com.fitmatch.dto.ticket.SessionPtCancellationDto;

import java.util.List;

/**
 * Quyết định §4.1: buổi tập mất PT vì đơn nghỉ được duyệt — KHÁCH quyết đổi PT
 * hay nhận hoàn phụ phí PT của ngày đó.
 */
public interface SessionPtCancellationService {

    /** Các quyết định còn treo của khách đang đăng nhập. */
    List<SessionPtCancellationDto> myPending(String customerUsername);

    /**
     * Khách chọn nhận hoàn tiền thay vì đổi PT. Hoàn đúng phụ phí PT của MỘT
     * ngày, đã chiết theo tỉ lệ voucher/điểm đã dùng.
     */
    SessionPtCancellationDto refund(String customerUsername, Long sessionId);

    /**
     * Khách đã chọn PT thay thế — đóng quyết định treo. Gọi từ
     * {@code TicketSchedulingService.setPt}, không phơi ra thành endpoint riêng.
     */
    void markReplaced(Long sessionId);

    /**
     * Chốt tự động cho buổi tới ngày mà khách vẫn chưa quyết: hoàn tiền. Khách
     * không được thiệt chỉ vì quên thao tác.
     *
     * @return số quyết định đã chốt
     */
    int autoResolveDueCancellations();
}
