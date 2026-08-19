package com.fitmatch.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * CA làm việc do Gym định nghĩa ở cấp CHI NHÁNH (V85) — nguồn sự thật mới của
 * lịch PT, thay {@code pt_availabilities} vốn do chính PT khai.
 *
 * <p>Ca gắn chi nhánh chứ không gắn Gym vì ràng buộc "ca phải nằm trong giờ mở
 * cửa" chỉ kiểm được khi biết chi nhánh — {@link OperatingHour} neo vào
 * {@code gym_branch_id}, và hai chi nhánh của cùng Gym mở cửa khác giờ.
 *
 * <p>Ca KHÔNG được vắt qua nửa đêm ({@code startTime < endTime} là bắt buộc):
 * {@code PtShiftAssignment.workDate} là một ngày duy nhất, ca 22:00-01:00 sẽ
 * đẩy slot sang ngày hôm sau và làm lệch {@code TrainingSession.sessionDate}.
 * Gym muốn ca đêm thì khai hai ca.
 */
@Entity
@Table(name = "gym_shifts",
        uniqueConstraints = @UniqueConstraint(name = "uk_gym_shift_branch_name",
                columnNames = {"gym_branch_id", "name"}),
        indexes = @Index(name = "idx_gym_shifts_branch", columnList = "gym_branch_id,active"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GymShift extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gym_branch_id", nullable = false)
    private GymBranch gymBranch;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    /**
     * Độ dài một slot khách đặt được, tính bằng phút. Khách chỉ chọn giờ BẮT
     * ĐẦU; giờ kết thúc do ca quyết (trước V85 do PT khai) và được chép vào
     * {@code TrainingSession.ptSlotEnd}.
     */
    @Column(name = "slot_minutes", nullable = false)
    @Builder.Default
    private Integer slotMinutes = 60;

    /**
     * Các thứ trong tuần ca này áp dụng, CSV ISO-8601 ("1,3,5" = T2/T4/T6).
     * Để CSV vì giá trị luôn được đọc TRỌN — sinh roster, kiểm chồng ca, đối
     * chiếu giờ mở cửa — không bao giờ lọc theo một thứ đơn lẻ.
     */
    @Column(name = "days_of_week", nullable = false, length = 20)
    private String daysOfWeek;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    /** Đọc CSV thành tập {@link DayOfWeek}; bỏ qua phần tử rác thay vì ném lỗi. */
    public Set<DayOfWeek> daysOfWeekSet() {
        if (daysOfWeek == null || daysOfWeek.isBlank()) {
            return Set.of();
        }
        Set<DayOfWeek> days = new LinkedHashSet<>();
        for (String part : daysOfWeek.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                int value = Integer.parseInt(trimmed);
                if (value >= 1 && value <= 7) {
                    days.add(DayOfWeek.of(value));
                }
            } catch (NumberFormatException ignored) {
                // Dữ liệu rác không được làm sập cả lưới phân ca.
            }
        }
        return days;
    }

    /** Ghi tập {@link DayOfWeek} về CSV đã sắp xếp — chuỗi ổn định, dễ so sánh. */
    public static String toDaysOfWeek(Set<DayOfWeek> days) {
        return days.stream().map(DayOfWeek::getValue).sorted()
                .map(String::valueOf).collect(Collectors.joining(","));
    }

    /** Tiện cho validate: các thứ hợp lệ dưới dạng số nguyên đã sắp xếp. */
    public static Set<DayOfWeek> parseDays(int... values) {
        return Arrays.stream(values).filter(v -> v >= 1 && v <= 7)
                .mapToObj(DayOfWeek::of)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
