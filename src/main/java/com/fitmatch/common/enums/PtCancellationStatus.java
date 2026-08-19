package com.fitmatch.common.enums;

/**
 * Quyết định §4.1: buổi tập bị PT xin nghỉ thì KHÁCH là người quyết, không
 * phải Gym. Trạng thái ở đây là trạng thái của QUYẾT ĐỊNH ĐANG TREO, không
 * phải của buổi tập — buổi vẫn SCHEDULED suốt quá trình vì vé có giá trị cả
 * ngày, khách mất PT chứ không mất quyền vào tập.
 */
public enum PtCancellationStatus {

    /** Đang chờ khách chọn: đổi PT khác hay nhận hoàn phụ phí PT của ngày đó. */
    PENDING_CUSTOMER,

    /** Khách đã chọn PT thay thế — không phát sinh hoàn tiền. */
    REPLACED,

    /**
     * Đã hoàn phụ phí PT của đúng ngày đó vào ví khách. Do khách tự chọn, hoặc
     * do job tự chốt khi tới ngày tập mà khách chưa quyết — khách không được
     * thiệt chỉ vì quên thao tác.
     */
    REFUNDED
}
