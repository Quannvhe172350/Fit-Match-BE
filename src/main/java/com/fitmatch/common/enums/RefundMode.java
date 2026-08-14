package com.fitmatch.common.enums;

/**
 * Câu 11: cách admin quyết mức hoàn cho một vé.
 */
public enum RefundMode {

    /** Hoàn toàn bộ số đã trả — gym không giữ lại đồng nào. */
    FULL,

    /**
     * Trừ số ngày đã qua: gym giữ perDay * elapsed, khách nhận phần còn lại.
     * Công thức và bất biến bảo toàn tiền nằm ở {@code PartialRefundCalculator}.
     */
    PARTIAL_ELAPSED
}
