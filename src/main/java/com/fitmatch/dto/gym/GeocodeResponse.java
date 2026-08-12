package com.fitmatch.dto.gym;

import com.fitmatch.service.support.GeoPoint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Kết quả ánh xạ địa chỉ &lt;-&gt; toạ độ trả cho FE (UC-18). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeocodeResponse {

    private BigDecimal latitude;
    private BigDecimal longitude;
    private String formattedAddress;
    private String placeId;

    /**
     * V65 — dịch vụ đã cấp {@link #placeId}. FE gửi NGƯỢC giá trị này lên khi lưu
     * hồ sơ để server biết nguồn gốc id mà không phải đoán; thiếu nó thì bản ghi
     * bị đánh dấu "không rõ nguồn" và job làm mới sẽ bỏ qua.
     */
    private com.fitmatch.common.enums.GeocodingProvider placeProvider;

    public static GeocodeResponse of(GeoPoint point) {
        return GeocodeResponse.builder()
                .latitude(point.latitude())
                .longitude(point.longitude())
                .formattedAddress(point.formattedAddress())
                .placeId(point.placeId())
                .placeProvider(point.provider())
                .build();
    }
}
