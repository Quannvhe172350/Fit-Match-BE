package com.fitmatch.dto.booking;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Tạo booking nháp (UC-031). Chỉ cần tối thiểu một đích (service/package/branch/pt)
 * để xác định Gym; các lựa chọn còn lại bổ sung dần qua UC-032.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateBookingRequest {

    private Long serviceId;

    private Long packageId;

    private Long branchId;

    private Long ptId;

    /** UC-049: đặt buổi tập từ gói ĐÃ MUA — miễn phí, trừ dần số buổi khi hoàn tất. */
    private Long customerPackageId;

    private LocalDateTime startAt;

    private LocalDateTime endAt;

    @Size(max = 500)
    private String note;
}
