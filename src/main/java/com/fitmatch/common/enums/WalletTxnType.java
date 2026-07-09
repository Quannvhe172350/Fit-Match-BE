package com.fitmatch.common.enums;

/**
 * Loại bút toán trên sổ cái ví (UC-057..062). Ledger chỉ INSERT, không sửa.
 */
public enum WalletTxnType {

    /** Tiền booking được giữ (escrow) ở ví nền tảng — RP1. */
    HOLD,

    /** Hoàn tiền cho khách từ phần đang giữ. */
    REFUND,

    /** Chuyển từ held sang pending settlement khi hoàn tất buổi tập. */
    MOVE_TO_PENDING,

    /** Giải ngân về available của Gym (đã trừ hoa hồng). */
    RELEASE,

    /** Phần hoa hồng nền tảng giữ lại khi release. */
    COMMISSION,

    /** Đóng băng một khoản (dispute/rủi ro). */
    FREEZE,

    /** Mở băng. */
    UNFREEZE,

    /** Rút tiền khỏi available của Gym. */
    WITHDRAWAL
}
