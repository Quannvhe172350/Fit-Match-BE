package com.fitmatch.service;

import com.fitmatch.dto.gym.PtShiftAssignRequest;
import com.fitmatch.dto.gym.PtShiftAssignResponse;
import com.fitmatch.dto.gym.ShiftRosterCellDto;
import com.fitmatch.dto.pt.PtShiftDto;

import java.time.LocalDate;
import java.util.List;

/** V86: Gym xếp PT vào ca; PT chỉ được ĐỌC lịch ca của mình. */
public interface PtShiftRosterService {

    /** Xếp lặp (khoảng ngày + các thứ) hoặc xếp lẻ (from == to). Idempotent. */
    PtShiftAssignResponse assign(String gymUsername, Long ptId, PtShiftAssignRequest request);

    /** Gỡ một ngày phân ca. Chặn nếu ca đó đã có buổi SCHEDULED (edge case §7.2). */
    void unassign(String gymUsername, Long ptId, Long assignmentId);

    /** Lưới phân ca của chi nhánh: hàng = PT, cột = ngày, ô = các ca. */
    List<ShiftRosterCellDto> roster(String gymUsername, Long branchId, LocalDate from, LocalDate to);

    /** Lịch ca của chính PT đang đăng nhập — read-only. */
    List<PtShiftDto> myShifts(String ptUsername, LocalDate from, LocalDate to);

    /**
     * Edge case §7.4: PT chuyển INACTIVE/SUSPENDED thì vô hiệu ca tương lai.
     * Tắt mềm (active=false) chứ không xoá — bật lại PT thì Gym còn thấy để xếp lại.
     *
     * @return số dòng phân ca đã vô hiệu
     */
    int deactivateFutureShifts(Long ptId);
}
