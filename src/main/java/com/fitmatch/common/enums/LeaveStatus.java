package com.fitmatch.common.enums;

/**
 * Vòng đời đơn nghỉ. Chỉ PENDING mới đổi được: PT tự huỷ, Gym duyệt hoặc từ
 * chối. Đơn đã APPROVED không rút lại được vì slot đã bị khoá và buổi tập của
 * khách có thể đã được xử lý (xem session_pt_cancellations) — muốn đi làm lại
 * thì Gym xếp ca bù, không phải đảo ngược đơn.
 */
public enum LeaveStatus {

    /** Chờ Gym duyệt. Đã tiêu hạn mức tháng (§4.2) ngay từ lúc này. */
    PENDING,

    /** Gym đã duyệt — slot trong phạm vi bị vô hiệu, khách không đặt được nữa. */
    APPROVED,

    /** Gym từ chối, bắt buộc có rejectReason. Không tiêu hạn mức. */
    REJECTED,

    /** PT tự huỷ khi còn PENDING. Không tiêu hạn mức. */
    CANCELLED
}
