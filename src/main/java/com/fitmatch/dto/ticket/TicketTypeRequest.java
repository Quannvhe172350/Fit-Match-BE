package com.fitmatch.dto.ticket;

import com.fitmatch.common.enums.TicketKind;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketTypeRequest {

    @NotBlank(message = "Ticket type name is required")
    @Size(max = 150)
    private String name;

    @Size(max = 1000)
    private String description;

    @NotNull(message = "Ticket kind is required")
    private TicketKind kind;

    /** Vé DAY bỏ trống hoặc để 1; vé PACKAGE bắt buộc >= 2. Service tự chuẩn hoá. */
    @Positive(message = "Day count must be positive")
    private Integer dayCount;

    /**
     * Độ dài mỗi buổi tập, tính bằng PHÚT. Bỏ trống = không ràng buộc (nhận mọi
     * ca). Có giá trị thì khách chỉ đặt được ca dài đúng ngần này.
     *
     * <p>Trần 8 tiếng: quá mốc đó thì không còn là một buổi tập mà là cả ngày mở
     * cửa, và chắc chắn là gõ nhầm đơn vị (nhập giờ vào ô phút).
     */
    @Positive(message = "Minutes per day must be positive")
    @Max(value = 480, message = "Minutes per day must be <= 480")
    private Integer minutesPerDay;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", message = "Price must be >= 0")
    private BigDecimal price;

    /** Câu 6: phụ phí PT tính cho MỖI NGÀY; null/0 = gym không tính thêm. */
    @DecimalMin(value = "0.0", message = "PT surcharge must be >= 0")
    private BigDecimal ptSurchargePerDay;

    /**
     * Câu 20: các chi nhánh được bán loại vé này. Bắt buộc ít nhất một — vé không
     * gắn chi nhánh nào thì không ai mua được, chỉ tạo rác trong catalog.
     */
    @NotEmpty(message = "At least one branch must be selected")
    private List<Long> branchIds;
}
