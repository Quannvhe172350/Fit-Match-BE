package com.fitmatch.dto.gym;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BranchRequest {

    @NotBlank(message = "Branch name is required")
    @Size(max = 150)
    private String name;

    @Size(max = 255)
    private String address;

    @Size(max = 100)
    private String city;

    @Size(max = 100)
    private String district;

    @Size(max = 30)
    private String phone;

    /**
     * UC-18 (V55): toạ độ do operator ghim trên bản đồ / chọn từ gợi ý Places.
     * Bỏ trống thì BE tự geocode từ address + district + city. Gửi kèm sẽ ghi đè
     * kết quả geocode — người biết vị trí thật luôn đúng hơn dịch vụ đoán địa chỉ.
     */
    @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
    private java.math.BigDecimal latitude;

    @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
    private java.math.BigDecimal longitude;

    /** UC-016: tiện ích của chi nhánh, phân tách bằng dấu phẩy (vd "Parking,Sauna,Pool"). */
    @Size(max = 1000)
    private String amenities;

    /** UC-017: sức chứa tối đa; null = không giới hạn. */
    @Positive(message = "Capacity must be positive")
    private Integer capacity;
}
