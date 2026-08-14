package com.fitmatch.common.enums;

/**
 * Vòng đời của vé — đơn vị escrow của hệ thống. Chạy song song với
 * {@link SettlementStatus}: vé mô tả quyền sử dụng, settlement mô tả dòng tiền.
 * Mọi chuyển trạng thái phải đi qua {@code TicketLifecycle}.
 */
public enum TicketStatus {

    /** Đã tạo, chờ khách chuyển khoản. Hết hạn đơn thanh toán -> CANCELLED. */
    PENDING_PAYMENT,

    /** Đã trả tiền (hoặc điểm/voucher phủ hết) — dùng được, đặt lịch được. */
    ACTIVE,

    /** Đã dùng hết số ngày của vé — mốc giải ngân tiền về gym. */
    USED_UP,

    /** Quá expires_at mà chưa dùng hết. Câu 32: hết hạn thì KHÔNG hoàn được nữa. */
    EXPIRED,

    /** Huỷ khi chưa thanh toán — không phát sinh dòng tiền nào. */
    CANCELLED,

    /** Đã hoàn tiền cho khách (toàn bộ hoặc trừ số ngày đã qua). */
    REFUNDED
}
