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

    public static GeocodeResponse of(GeoPoint point) {
        return GeocodeResponse.builder()
                .latitude(point.latitude())
                .longitude(point.longitude())
                .formattedAddress(point.formattedAddress())
                .placeId(point.placeId())
                .build();
    }
}
