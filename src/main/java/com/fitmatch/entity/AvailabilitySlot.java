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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

/**
 * Khung giờ rảnh lặp hàng tuần của PT (UC-028). Lịch của chi nhánh dùng
 * operating_hours (UC-017); blocked_times (UC-029) chặn theo ngày cụ thể.
 * Schema: availability_slots(id, pt_profile_id FK, day_of_week 1-7,
 * start_time, end_time, + audit).
 */
@Entity
@Table(name = "availability_slots", indexes =
        @Index(name = "idx_availability_pt_day", columnList = "pt_profile_id,day_of_week"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AvailabilitySlot extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pt_profile_id", nullable = false)
    private PtProfile ptProfile;

    /** 1 = Thứ hai ... 7 = Chủ nhật (ISO-8601). */
    @Column(name = "day_of_week", nullable = false)
    private Integer dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;
}
