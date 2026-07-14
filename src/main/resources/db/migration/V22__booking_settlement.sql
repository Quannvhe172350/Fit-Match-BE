-- =====================================================================
-- V22 (UC-049/057..059): Trạng thái dòng tiền escrow trên booking.
-- completed_at        : thời điểm buổi tập hoàn tất (UC-049)
-- settlement_status   : NONE/HELD/REFUND_PENDING/PENDING_RELEASE/RELEASED/REFUNDED
-- settlement_amount   : số tiền đã chuyển sang pending settlement (UC-058)
-- settlement_pending_at: mốc bắt đầu holding period trước khi release (UC-059)
-- =====================================================================

alter table bookings
    add column completed_at datetime(6) null,
    add column settlement_status varchar(20) not null default 'NONE',
    add column settlement_amount decimal(14,2) null,
    add column settlement_pending_at datetime(6) null;

create index idx_bookings_settlement on bookings (settlement_status, settlement_pending_at);

-- Backfill: booking đã giữ tiền (đơn thanh toán PAID) trước khi có cột này.
update bookings b
    join payment_orders po on po.booking_id = b.id and po.status = 'PAID'
set b.settlement_status = 'HELD'
where b.settlement_status = 'NONE';
