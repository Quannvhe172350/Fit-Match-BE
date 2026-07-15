package com.fitmatch.common.enums;

/** Quyết định giải quyết tranh chấp (UC-066) — quyết định cách áp dụng tài chính (UC-067). */
public enum DisputeResolution {

    /** Hoàn toàn bộ phần đang giữ cho khách. */
    REFUND_FULL,

    /** Hoàn một phần cho khách; phần còn lại về Gym (pending settlement). */
    REFUND_PARTIAL,

    /** Chia đôi: hoàn refundAmount cho khách, phần còn lại về Gym. */
    SPLIT,

    /** Giải phóng toàn bộ cho Gym (không hoàn) — về pending settlement. */
    RELEASE_TO_GYM,

    /** Không hành động tài chính — tiền tiếp tục về Gym như bình thường. */
    NO_ACTION,

    /** Phạt Gym/PT: hoàn cho khách + ghi nhận vi phạm chất lượng. */
    PENALTY
}
