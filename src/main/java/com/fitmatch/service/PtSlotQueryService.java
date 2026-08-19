package com.fitmatch.service;

import com.fitmatch.dto.pt.PtSlotCellDto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Lưới chọn PT của KHÁCH. Thay {@code PtDailyAvailabilityService}: contract ra
 * ngoài giữ nguyên ({@link PtSlotCellDto}), chỉ nguồn dữ liệu đổi từ
 * {@code pt_availabilities} sang ca đã xếp trừ đơn nghỉ đã duyệt.
 *
 * <p>Hai phần khai lịch cũ ({@code mySlots}, {@code save}) biến mất hoàn toàn —
 * PT không còn quyền khai lịch.
 */
public interface PtSlotQueryService {

    /** Chọn giờ trước: PT nào của chi nhánh còn trống đúng khung đó. */
    List<PtSlotCellDto> search(Long branchId, LocalDate date, LocalTime startTime);

    /** Lưới ngày x giờ. Có ptId = lưới riêng một PT; bỏ ptId = gộp cả chi nhánh. */
    List<PtSlotCellDto> grid(Long branchId, Long ptId, LocalDate from, LocalDate to);
}
