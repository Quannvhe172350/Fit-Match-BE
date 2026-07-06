package com.fitmatch.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

/**
 * Giờ hoạt động theo ngày trong tuần của một chi nhánh (UC-017).
 * Mỗi (branch, dayOfWeek) một dòng; closed=true = nghỉ cả ngày.
 * Schema: operating_hours(id, gym_branch_id FK, day_of_week 1-7, open_time,
 * close_time, closed, + audit) UNIQUE(gym_branch_id, day_of_week).
 */
@Entity
@Table(name = "operating_hours", uniqueConstraints =
        @UniqueConstraint(name = "uk_operating_hours_branch_day", columnNames = {"gym_branch_id", "day_of_week"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OperatingHour extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gym_branch_id", nullable = false)
    private GymBranch gymBranch;

    /** 1 = Thứ hai ... 7 = Chủ nhật (ISO-8601). */
    @Column(name = "day_of_week", nullable = false)
    private Integer dayOfWeek;

    @Column(name = "open_time")
    private LocalTime openTime;

    @Column(name = "close_time")
    private LocalTime closeTime;

    /** Nghỉ cả ngày. */
    @Column(nullable = false)
    @Builder.Default
    private boolean closed = false;
}
