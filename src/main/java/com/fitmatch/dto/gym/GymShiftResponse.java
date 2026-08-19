package com.fitmatch.dto.gym;

import com.fitmatch.entity.GymShift;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

/** Một ca của chi nhánh, kèm số slot cắt được để Gym thấy ngay hệ quả của slotMinutes. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GymShiftResponse {

    private Long id;
    private Long branchId;
    private String branchName;
    private String name;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer slotMinutes;
    private List<Integer> daysOfWeek;
    private boolean active;

    /** Số khung giờ khách đặt được trong một lần lên ca. */
    private int slotCount;

    public static GymShiftResponse of(GymShift shift, int slotCount) {
        return GymShiftResponse.builder()
                .id(shift.getId())
                .branchId(shift.getGymBranch() != null ? shift.getGymBranch().getId() : null)
                .branchName(shift.getGymBranch() != null ? shift.getGymBranch().getName() : null)
                .name(shift.getName())
                .startTime(shift.getStartTime())
                .endTime(shift.getEndTime())
                .slotMinutes(shift.getSlotMinutes())
                .daysOfWeek(shift.daysOfWeekSet().stream().map(DayOfWeek::getValue).sorted().toList())
                .active(shift.isActive())
                .slotCount(slotCount)
                .build();
    }
}
