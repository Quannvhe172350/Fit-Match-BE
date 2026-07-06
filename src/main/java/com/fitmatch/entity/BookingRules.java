package com.fitmatch.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Quy tắc thanh toán/đặt lịch cấu hình trên dịch vụ và gói tập (UC-026).
 * Phí hoa hồng nền tảng cấu hình riêng ở cấp platform (UC-072), không nằm ở đây.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingRules {

    /** % đặt cọc khi checkout (0-100); null = thanh toán đủ 100%. */
    @Column(name = "deposit_percent")
    private Integer depositPercent;

    /** Hủy miễn phí nếu trước giờ hẹn ít nhất X giờ; null = theo chính sách Gym. */
    @Column(name = "free_cancellation_hours")
    private Integer freeCancellationHours;

    /** Phải đặt trước tối thiểu X giờ; null = không ràng buộc. */
    @Column(name = "min_notice_hours")
    private Integer minNoticeHours;
}
