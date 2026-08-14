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

    /**
     * UC-016: BẮT BUỘC. Chi nhánh là điểm khách tới tập và là nơi phát sinh mọi
     * việc cần gọi trực tiếp — đổi giờ, tới nơi không vào được, tranh chấp buổi
     * tập. Trước đây để trống được, nên tổng đài/CSKH gặp chi nhánh không có số
     * nào để gọi và phải lần ngược lên số của gym.
     *
     * <p>Ràng buộc này áp cho cả sửa: chi nhánh cũ chưa có số thì lần sửa tiếp
     * theo phải bổ sung. Cố ý — đó là cách duy nhất để dữ liệu cũ được lấp đầy.
     */
    @NotBlank(message = "Branch phone is required")
    @Size(max = 30)
    @com.fitmatch.common.validation.VietnamPhone
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

    /**
     * Metadata của chính gợi ý Places mà operator đã chọn, gửi kèm toạ độ. Không
     * có nó thì mỗi lần ghim toạ độ tay là hai cột này bị bỏ trống dù FE đang cầm
     * sẵn giá trị đúng. Bỏ trống -> giữ nguyên giá trị đang lưu.
     */
    @Size(max = 255)
    private String placeId;

    /**
     * V65 — dịch vụ đã cấp {@link #placeId}, lấy nguyên văn từ response gợi ý địa
     * điểm của server. Thiếu giá trị này thì bản ghi bị coi là "không rõ nguồn" và
     * job làm mới toạ độ sẽ bỏ qua nó.
     */
    private com.fitmatch.common.enums.GeocodingProvider placeProvider;

    @Size(max = 500)
    private String formattedAddress;

    /**
     * V60 — toạ độ do người kéo ghim trên bản đồ chứ không lấy nguyên từ gợi ý
     * Places. Job làm mới định kỳ bỏ qua bản ghi được đánh dấu, nếu không nó kéo
     * ghim về chỗ Google nói và xoá sạch công sửa tay. Null = coi như false.
     */
    private Boolean coordinatesPinned;

    /** UC-016: tiện ích của chi nhánh, phân tách bằng dấu phẩy (vd "Parking,Sauna,Pool"). */
    @Size(max = 1000)
    private String amenities;

    /** UC-017: sức chứa tối đa; null = không giới hạn. */
    @Positive(message = "Capacity must be positive")
    private Integer capacity;
}
