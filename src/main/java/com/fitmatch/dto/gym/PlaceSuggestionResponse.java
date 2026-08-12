package com.fitmatch.dto.gym;

import com.fitmatch.common.enums.GeocodingProvider;
import com.fitmatch.service.support.GeoSuggestion;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Một gợi ý địa điểm trả cho ô nhập địa chỉ (UC-18, V65).
 *
 * <p>Thay cho Places Autocomplete chạy phía trình duyệt: khoá API nằm lại ở
 * server, và FE không còn phụ thuộc SDK của bất kỳ nhà cung cấp nào.
 *
 * <p>{@code district}/{@code city} là chuỗi THÔ từ nhà cung cấp — FE tự chuẩn hoá
 * về danh mục VN_CITIES trước khi điền vào form, vì bộ lọc marketplace so khớp
 * theo đúng chuỗi của danh mục đó.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaceSuggestionResponse {

    /** Nhãn ngắn để hiện lại trong ô nhập sau khi chọn. */
    private String label;

    /** Địa chỉ đầy đủ — đây mới là thứ được lưu vào cột address. */
    private String formattedAddress;

    private BigDecimal latitude;
    private BigDecimal longitude;
    private String placeId;
    private GeocodingProvider placeProvider;
    private String district;
    private String city;

    public static PlaceSuggestionResponse of(GeoSuggestion suggestion) {
        return PlaceSuggestionResponse.builder()
                .label(suggestion.label())
                .formattedAddress(suggestion.formattedAddress())
                .latitude(suggestion.latitude())
                .longitude(suggestion.longitude())
                .placeId(suggestion.placeId())
                .placeProvider(suggestion.provider())
                .district(suggestion.district())
                .city(suggestion.city())
                .build();
    }
}
