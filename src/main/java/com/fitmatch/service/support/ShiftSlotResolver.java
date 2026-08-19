package com.fitmatch.service.support;

import com.fitmatch.common.enums.LeaveScope;
import com.fitmatch.entity.GymShift;
import com.fitmatch.entity.PtLeaveRequest;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Toán học của mô hình ca: cắt một CA thành các SLOT cố định, và trả lời "slot
 * này có bị đơn nghỉ phủ không".
 *
 * <p>Tách hẳn khỏi service vì đúng phép tính này bị hỏi từ bốn phía —
 * {@code PtSlotValidator} (khi khách đặt), lưới chọn PT của khách, lưới phân ca
 * của Gym, và lúc duyệt đơn nghỉ (tìm buổi bị phủ). Một chỗ sai thì bốn chỗ
 * lệch nhau, và lệch kiểu im lặng.
 *
 * <p>Quyết định §4.3: slot CỐ ĐỊNH theo {@code slotMinutes}, không cho khách
 * chọn giờ tự do. Bắt buộc chứ không phải sở thích: {@code PtSlotValidator}
 * kiểm trùng bằng phép SO BẰNG {@code pt_slot_start}, nên giờ tự do sẽ để lọt
 * 18:00-19:00 và 18:30-19:30 cùng tồn tại.
 */
@Component
public class ShiftSlotResolver {

    /** Một khung giờ đặt được, đã gắn với ca sinh ra nó. */
    public record Slot(GymShift shift, LocalTime start, LocalTime end) {
    }

    /**
     * Cắt ca thành các slot liên tiếp. Slot cuối không đủ {@code slotMinutes}
     * thì bị bỏ — thà thiếu một khung còn hơn bán một buổi ngắn hơn cam kết.
     */
    public List<Slot> slotsOf(GymShift shift) {
        List<Slot> slots = new ArrayList<>();
        int minutes = slotMinutes(shift);
        LocalTime cursor = shift.getStartTime();
        while (!cursor.plusMinutes(minutes).isAfter(shift.getEndTime())) {
            LocalTime end = cursor.plusMinutes(minutes);
            slots.add(new Slot(shift, cursor, end));
            cursor = end;
            // Ca không vắt nửa đêm (bất biến của GymShift) nên cursor luôn tăng;
            // vòng lặp không thể quay vòng qua 00:00.
            if (cursor.equals(LocalTime.MIDNIGHT)) {
                break;
            }
        }
        return slots;
    }

    /**
     * Giờ kết thúc của slot bắt đầu lúc {@code slotStart} trong ca này, hoặc
     * null nếu {@code slotStart} không nằm đúng lưới slot của ca.
     */
    public LocalTime slotEndOrNull(GymShift shift, LocalTime slotStart) {
        if (slotStart.isBefore(shift.getStartTime())) {
            return null;
        }
        int minutes = slotMinutes(shift);
        long offset = Duration.between(shift.getStartTime(), slotStart).toMinutes();
        if (offset % minutes != 0) {
            return null;
        }
        LocalTime end = slotStart.plusMinutes(minutes);
        return end.isAfter(shift.getEndTime()) ? null : end;
    }

    /** Hai khoảng giờ có giao nhau không (nửa mở: kề nhau KHÔNG tính là giao). */
    public boolean overlaps(LocalTime aStart, LocalTime aEnd, LocalTime bStart, LocalTime bEnd) {
        return aStart.isBefore(bEnd) && bStart.isBefore(aEnd);
    }

    /** Hai ca có chồng giờ không — dùng khi Gym tạo ca và khi xếp PT vào ca. */
    public boolean shiftsOverlap(GymShift a, GymShift b) {
        return overlaps(a.getStartTime(), a.getEndTime(), b.getStartTime(), b.getEndTime());
    }

    /**
     * Đơn nghỉ này có phủ lên khoảng giờ đó của ngày đó không.
     *
     * @param shift ca chứa khoảng giờ; cần cho scope = SHIFT, có thể null khi
     *              đang hỏi về một khoảng giờ không thuộc ca nào
     */
    public boolean leaveCovers(PtLeaveRequest leave, LocalDate date,
                               LocalTime start, LocalTime end, GymShift shift) {
        if (date.isBefore(leave.getFromDate()) || date.isAfter(leave.getToDate())) {
            return false;
        }
        LeaveScope scope = leave.getScope();
        if (scope == LeaveScope.FULL_DAY) {
            return true;
        }
        if (scope == LeaveScope.SHIFT) {
            return shift != null && leave.getShifts().stream()
                    .anyMatch(s -> s.getId().equals(shift.getId()));
        }
        // TIME_RANGE: thiếu giờ thì coi như phủ cả ngày — an toàn hơn là để lọt.
        if (leave.getStartTime() == null || leave.getEndTime() == null) {
            return true;
        }
        return overlaps(start, end, leave.getStartTime(), leave.getEndTime());
    }

    /** Bất kỳ đơn nào trong danh sách phủ khoảng giờ này. */
    public boolean anyLeaveCovers(Collection<PtLeaveRequest> leaves, LocalDate date,
                                  LocalTime start, LocalTime end, GymShift shift) {
        return leaves.stream().anyMatch(l -> leaveCovers(l, date, start, end, shift));
    }

    private int slotMinutes(GymShift shift) {
        Integer minutes = shift.getSlotMinutes();
        return minutes == null || minutes <= 0 ? 60 : minutes;
    }
}
