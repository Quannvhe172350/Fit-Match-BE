package com.fitmatch.dto.ticket;

import com.fitmatch.entity.PlatformTicketConfig;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Câu 32: hạn dùng vé do Admin cấu hình. Đổi ở đây chỉ ảnh hưởng vé bán TỪ ĐÂY
 * TRỞ ĐI — vé đã bán giữ nguyên expires_at đã chốt lúc mua.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketExpiryConfigDto {

    @NotNull(message = "dayTicketExpiryDays is required")
    @Positive(message = "dayTicketExpiryDays must be positive")
    @Max(value = 3650, message = "dayTicketExpiryDays is unreasonably large")
    private Integer dayTicketExpiryDays;

    @NotNull(message = "packageTicketExpiryDays is required")
    @Positive(message = "packageTicketExpiryDays must be positive")
    @Max(value = 3650, message = "packageTicketExpiryDays is unreasonably large")
    private Integer packageTicketExpiryDays;

    public static TicketExpiryConfigDto of(PlatformTicketConfig c) {
        return TicketExpiryConfigDto.builder()
                .dayTicketExpiryDays(c.getDayTicketExpiryDays())
                .packageTicketExpiryDays(c.getPackageTicketExpiryDays())
                .build();
    }
}
