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
 * @param ward      PHƯỜNG/XÃ thô từ nhà cung cấp.
 *                  <p>V66: trước đây field này là quận/huyện. Từ đợt sắp xếp đơn
 *                  vị hành chính 2025, Việt Nam bỏ cấp huyện — dưới tỉnh/thành là
 *                  thẳng phường/xã, nên đây mới là cấp cần lấy. Các field cấp
 *                  huyện mà nhà cung cấp còn trả về là dữ liệu tồn đọng và SAI:
 *                  {@code county} của Geoapify trả "Hoàn Kiếm" cho một điểm ở Cầu
 *                  Giấy, "Thanh Khê" cho một điểm ở Hải Châu.
 * @param city      tỉnh/thành thô từ nhà cung cấp
 * @param provider  dịch vụ đã cấp {@code placeId} — đi kèm để bản ghi lưu xuống
 *                  DB biết nguồn gốc id, xem {@code GymProfile#placeProvider}
 */
public record GeoSuggestion(String label, String formattedAddress,
                            BigDecimal latitude, BigDecimal longitude,
                            String placeId, GeocodingProvider provider,
                            String ward, String city) {
}
