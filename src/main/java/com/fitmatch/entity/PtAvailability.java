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

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Khung giờ rảnh của PT theo NGÀY CỤ THỂ (câu 26) — thay
 * {@code availability_slots} vốn lặp theo thứ trong tuần.
 *
 * <p>Hệ quả: không cần bảng chặn lịch riêng. PT bận thì đơn giản là không khai
 * khung giờ cho ngày đó, nên {@code blocked_times} bị bỏ hoàn toàn.
 */
@Entity
@Table(name = "pt_availabilities",
        uniqueConstraints = @UniqueConstraint(name = "uk_pt_avail_slot",
                columnNames = {"pt_profile_id", "slot_date", "start_time"}),
        indexes = {
                @Index(name = "idx_pt_avail_pt_date", columnList = "pt_profile_id,slot_date"),
                @Index(name = "idx_pt_avail_date_time", columnList = "slot_date,start_time")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PtAvailability extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pt_profile_id", nullable = false)
    private PtProfile ptProfile;

    @Column(name = "slot_date", nullable = false)
    private LocalDate slotDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;
}
