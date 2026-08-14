package com.fitmatch.dto.gym;

import com.fitmatch.service.support.GeoPoint;
import com.fitmatch.service.support.GeoSuggestion;
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

    /**
     * Phường/xã + tỉnh/thành THÔ từ nhà cung cấp — chỉ có ở chiều toạ độ -&gt; địa
     * chỉ (xem {@code GeocodingService#reverseGeocode}).
     *
     * <p>Để form địa chỉ gym điền được hai ô riêng ngay khi operator thả ghim trên
     * bản đồ, thay vì bắt họ tự gõ lại một thứ mà hệ thống vừa tra ra.
     *
     * <p>V66: là PHƯỜNG/XÃ chứ không phải quận/huyện — cấp huyện đã bỏ từ đợt sắp
     * xếp đơn vị hành chính 2025, xem javadoc {@code GeoSuggestion#ward}.
     *
     * <p>Null ở chiều xuôi (địa chỉ -&gt; toạ độ): ở đó chính form đã cầm sẵn hai giá
     * trị này, ghi đè lại chỉ tổ xoá mất thứ operator vừa gõ.
     */
    private String ward;
    private String city;

    public static GeocodeResponse of(GeoPoint point) {
        return GeocodeResponse.builder()
                .latitude(point.latitude())
                .longitude(point.longitude())
                .formattedAddress(point.formattedAddress())
                .placeId(point.placeId())
                .placeProvider(point.provider())
                .build();
    }

    /** Chiều toạ độ -&gt; địa chỉ: mang theo cả các mảnh hành chính đã tách. */
    public static GeocodeResponse of(GeoSuggestion suggestion) {
        return GeocodeResponse.builder()
                .latitude(suggestion.latitude())
                .longitude(suggestion.longitude())
                .formattedAddress(suggestion.formattedAddress())
                .placeId(suggestion.placeId())
                .placeProvider(suggestion.provider())
                .ward(suggestion.ward())
                .city(suggestion.city())
                .build();
    }
}
