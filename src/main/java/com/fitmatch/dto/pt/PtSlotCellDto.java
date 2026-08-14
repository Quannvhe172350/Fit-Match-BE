package com.fitmatch.dto.pt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Một ô của lưới ngày x giờ ở màn chọn PT. Dùng chung cho cả hai chiều tìm
 * kiếm: chọn giờ trước rồi lọc PT, và chọn PT trước rồi xem ngày rảnh.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PtSlotCellDto {

    private Long ptProfileId;
    private String ptName;
    private BigDecimal ptAvgRating;

    private LocalDate date;
    private LocalTime startTime;
    private LocalTime endTime;

    /** true = đã có người đặt; FE hiển thị mờ và không cho bấm. */
    private boolean taken;
}
