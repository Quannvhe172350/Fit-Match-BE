package com.fitmatch.dto.pt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Quyết định #8: lưu LUÔN thành công. Khai dưới ngưỡng chỉ trả cảnh báo và
 * không hề ảnh hưởng khả năng khách đặt PT đó.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PtAvailabilitySaveResponse {

    /** Số khung giờ đã lưu trong khoảng vừa gửi. */
    private int saved;

    /** Tổng số NGÀY có khung giờ, tính từ hôm nay trở đi. */
    private long daysWithSlots;

    /** Ngưỡng khuyến nghị để FE hiện "14/20 ngày". */
    private int threshold;

    /** null = đạt ngưỡng. Khác null thì FE hiện banner vàng, nút Lưu vẫn bật. */
    private String warning;
}
