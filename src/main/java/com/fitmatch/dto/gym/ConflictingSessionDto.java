package com.fitmatch.dto.gym;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Buổi tập đang vướng một thao tác bị chặn (gỡ ca, xoá ca, gỡ phân công PT).
 * Trả về danh sách thay vì chỉ một thông điệp: Gym cần bấm được vào từng buổi
 * để xử lý, chứ không phải tự đi dò trong lịch.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConflictingSessionDto {

    private Long sessionId;
    private LocalDate date;
    private LocalTime slotStart;
    private LocalTime slotEnd;
    private String customerName;
    private String ptName;
}
