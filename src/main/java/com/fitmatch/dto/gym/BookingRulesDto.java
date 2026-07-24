package com.fitmatch.dto.gym;

import com.fitmatch.entity.BookingRules;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Quy tắc thanh toán/đặt lịch của dịch vụ hoặc gói tập (UC-026).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingRulesDto {

    @Min(value = 0, message = "Deposit percent must be between 0 and 100")
    @Max(value = 100, message = "Deposit percent must be between 0 and 100")
    private Integer depositPercent;

    @Min(value = 0, message = "Free cancellation hours must be >= 0")
    private Integer freeCancellationHours;

    @Min(value = 0, message = "Min notice hours must be >= 0")
    private Integer minNoticeHours;

    /**
     * P0-0.2: khóa tạm đặt cọc MỘT PHẦN. Hiện chưa có luồng thu số dư (total - deposit)
     * khi hoàn tất buổi tập, nên đặt cọc &lt; 100% khiến nền tảng không bao giờ thu phần
     * còn lại (rò rỉ tài chính). Chỉ cho phép null (trả đủ 100%) hoặc 100 cho tới khi
     * dựng luồng thu số dư (UC-034/049). Gỡ guard này khi luồng đó hoàn thiện.
     */
    @jakarta.validation.constraints.AssertTrue(
            message = "Partial deposit is not supported yet; depositPercent must be empty (full payment) or 100")
    public boolean isDepositPercentSupported() {
        return depositPercent == null || depositPercent == 100;
    }

    public BookingRules toEntity() {
        return BookingRules.builder()
                .depositPercent(depositPercent)
                .freeCancellationHours(freeCancellationHours)
                .minNoticeHours(minNoticeHours)
                .build();
    }

    public static BookingRulesDto of(BookingRules r) {
        if (r == null) {
            return null;
        }
        return BookingRulesDto.builder()
                .depositPercent(r.getDepositPercent())
                .freeCancellationHours(r.getFreeCancellationHours())
                .minNoticeHours(r.getMinNoticeHours())
                .build();
    }
}
