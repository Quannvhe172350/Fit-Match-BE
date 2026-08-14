package com.fitmatch.service;

import com.fitmatch.dto.pt.PtAvailabilityRequest;
import com.fitmatch.dto.pt.PtAvailabilitySaveResponse;
import com.fitmatch.dto.pt.PtAvailabilitySlotDto;
import com.fitmatch.dto.pt.PtSlotCellDto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Lịch rảnh của PT theo NGÀY CỤ THỂ (câu 26) — thay
 * {@link PtAvailabilityService} vốn khai lặp theo thứ trong tuần. Hai service
 * cùng tồn tại ở P2 và bản cũ bị xoá ở P4.
 */
public interface PtDailyAvailabilityService {

    /** Khung giờ PT đang đăng nhập đã khai trong một khoảng ngày. */
    List<PtAvailabilitySlotDto> mySlots(String ptUsername, LocalDate from, LocalDate to);

    /**
     * Thay toàn bộ khung giờ trong [from, to]. Luôn lưu thành công; khai dưới
     * ngưỡng chỉ trả cảnh báo (quyết định #8). Không cho xoá khung giờ đã có
     * người đặt.
     */
    PtAvailabilitySaveResponse save(String ptUsername, PtAvailabilityRequest request);

    /** Chọn giờ trước: PT nào của chi nhánh rảnh đúng khung này. */
    List<PtSlotCellDto> search(Long branchId, LocalDate date, LocalTime startTime);

    /** Chọn PT trước (ptId != null) hoặc xem lưới gộp toàn chi nhánh (ptId = null). */
    List<PtSlotCellDto> grid(Long branchId, Long ptId, LocalDate from, LocalDate to);
}
