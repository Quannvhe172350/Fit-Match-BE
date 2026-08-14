package com.fitmatch.dto.gym;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitGymRegistrationRequest {

    @NotBlank(message = "Gym name is required")
    @Size(max = 150)
    private String gymName;

    @Size(max = 2000)
    private String description;

    @Size(max = 255)
    private String address;

    @Size(max = 100)
    private String city;

    @Size(max = 100)
    private String district;

    /** Số tổng đài của gym — không bắt buộc, nhưng đã điền thì phải gọi được. */
    @Size(max = 30)
    @com.fitmatch.common.validation.VietnamPhone
    private String phone;

    /** UC-18 (V55): toạ độ do operator ghim trên bản đồ; bỏ trống thì BE tự geocode. */
    @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
    private java.math.BigDecimal latitude;

    @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
    private java.math.BigDecimal longitude;

    /** Metadata của gợi ý Places đi kèm toạ độ ghim; bỏ trống -> giữ nguyên giá trị đang lưu. */
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
     * Places; job làm mới định kỳ sẽ bỏ qua bản ghi này. Null = coi như false.
     */
    private Boolean coordinatesPinned;

    @NotEmpty(message = "At least one verification document is required")
    @Valid
    private List<GymDocumentDto> documents;
}
