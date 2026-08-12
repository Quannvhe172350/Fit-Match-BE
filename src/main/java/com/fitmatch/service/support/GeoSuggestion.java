package com.fitmatch.service.support;

import com.fitmatch.common.enums.GeocodingProvider;

import java.math.BigDecimal;

/**
 * Một gợi ý địa điểm cho ô nhập địa chỉ (UC-18, V65).
 *
 * <p>Mang sẵn toạ độ chứ không chỉ mang định danh: nếu chỉ trả {@code placeId}
 * thì mỗi lần người dùng chọn một gợi ý lại tốn thêm một lượt gọi "lấy chi tiết"
 * — nhân với số lần gõ thì đó là phần tốn hạn mức nhất của cả hệ thống.
 *
 * @param label     nhãn NGẮN để hiện lại trong ô nhập
 * @param district  quận/huyện thô từ nhà cung cấp; FE tự chuẩn hoá về danh mục VN
 * @param city      tỉnh/thành thô từ nhà cung cấp
 * @param provider  dịch vụ đã cấp {@code placeId} — đi kèm để bản ghi lưu xuống
 *                  DB biết nguồn gốc id, xem {@code GymProfile#placeProvider}
 */
public record GeoSuggestion(String label, String formattedAddress,
                            BigDecimal latitude, BigDecimal longitude,
                            String placeId, GeocodingProvider provider,
                            String district, String city) {
}
