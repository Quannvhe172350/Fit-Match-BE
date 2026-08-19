package com.fitmatch.entity;

import com.fitmatch.common.enums.ShiftSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

import java.time.LocalDate;

/**
 * Một PT làm một CA vào một NGÀY cụ thể (V86) — kết quả của việc Gym xếp ca.
 *
 * <p>Materialize theo từng ngày thay vì lưu quy tắc lặp: cả lưới phân ca lẫn
 * {@code PtSlotValidator} đều hỏi đúng một câu "ngày D, PT P có ca nào" — trả
 * lời bằng index lookup rẻ hơn nhiều so với giải quy tắc lặp mỗi lần đọc.
 *
 * <p>{@link #active} là tắt MỀM: PT chuyển INACTIVE thì ca tương lai bị vô
 * hiệu chứ không bị xoá, để khi Gym bật lại PT thì còn thấy lịch cũ mà xếp lại
 * có chủ đích.
 */
@Entity
@Table(name = "pt_shift_assignments",
        uniqueConstraints = @UniqueConstraint(name = "uk_pt_shift_date",
                columnNames = {"pt_profile_id", "gym_shift_id", "work_date"}),
        indexes = {
                @Index(name = "idx_pt_shift_pt_date", columnList = "pt_profile_id,work_date"),
                @Index(name = "idx_pt_shift_shift_date", columnList = "gym_shift_id,work_date")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PtShiftAssignment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pt_profile_id", nullable = false)
    private PtProfile ptProfile;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gym_shift_id", nullable = false)
    private GymShift gymShift;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ShiftSource source = ShiftSource.RECURRING;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
