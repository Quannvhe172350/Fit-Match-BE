package com.fitmatch.dto.gym;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

/**
 * Kết quả một lượt xếp ca. Trả về cả phần BỊ BỎ QUA chứ không im lặng: Gym bấm
 * "xếp cả tháng 9" mà chi nhánh nghỉ Chủ nhật thì phải thấy rõ 4 ngày không
 * được xếp, nếu không sẽ tưởng PT có ca mà thực ra không.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PtShiftAssignResponse {

    /** Số dòng phân ca mới tạo (đã trừ những ngày vốn đã có — thao tác idempotent). */
    private int created;

    /** Ngày đã có sẵn dòng phân ca, không tạo thêm. */
    private List<LocalDate> alreadyAssigned;

    /** Ngày bị bỏ vì chi nhánh đóng cửa (OperatingHour.closed). */
    private List<LocalDate> skippedClosed;

    /** Ngày bị bỏ vì PT đã có ca khác CHỒNG GIỜ hôm đó, kèm mô tả ca vướng. */
    private List<String> skippedOverlap;
}
