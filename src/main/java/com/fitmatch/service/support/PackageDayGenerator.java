package com.fitmatch.service.support;

import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Câu 27: vé gói n ngày là n ngày LỊCH LIÊN TIẾP kể từ ngày bắt đầu — đếm
 * thẳng, không nhảy qua ngày nghỉ của phòng gym, không né cuối tuần.
 *
 * <p>Giờ mở cửa (operating_hours) chỉ còn là thông tin marketplace và không
 * tham gia sinh lịch, nên ở đây không có nhánh nào đọc tới nó.
 */
@Component
public class PackageDayGenerator {

    /** Sinh đúng {@code dayCount} ngày liên tiếp bắt đầu từ {@code startDate}. */
    public List<LocalDate> generate(LocalDate startDate, int dayCount) {
        if (startDate == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "startDate is required");
        }
        if (dayCount <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "dayCount must be positive");
        }
        List<LocalDate> days = new ArrayList<>(dayCount);
        for (int i = 0; i < dayCount; i++) {
            days.add(startDate.plusDays(i));
        }
        return days;
    }
}
