package com.fitmatch.common.enums;

/** Loại bút toán điểm thưởng (UC-073). */
public enum LoyaltyTxnType {

    /** Tích điểm khi hoàn tất buổi tập. */
    EARN,

    /** Tiêu điểm để giảm giá booking. */
    REDEEM,

    /** Hoàn điểm khi booking bị hủy trước thanh toán (giải phóng điểm đã giữ). */
    REFUND
}
