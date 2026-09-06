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

    /**
     * Chọn giờ trước: PT nào của chi nhánh còn trống đúng khung đó.
     *
     * @param minutes thời lượng buổi (từ vé); null = một slot, hành vi cũ
     */
    List<PtSlotCellDto> search(Long branchId, LocalDate date, LocalTime startTime, Integer minutes);

    /**
     * Lưới ngày x giờ. Có ptId = lưới riêng một PT; bỏ ptId = gộp cả chi nhánh.
     *
     * <p>{@code minutes} đổi ý nghĩa của một ô: không còn là "một slot của ca" mà
     * là "một khung có thể BẮT ĐẦU buổi dài ngần ấy phút" — chuỗi slot đứng sau
     * nó được phép vắt qua nhiều ca liền nhau. {@code endTime} của ô là điểm kết
     * thúc của cả chuỗi, nên FE vẫn chỉ đọc hai mốc giờ như cũ.
     */
    List<PtSlotCellDto> grid(Long branchId, Long ptId, LocalDate from, LocalDate to,
                             Integer minutes);
}
