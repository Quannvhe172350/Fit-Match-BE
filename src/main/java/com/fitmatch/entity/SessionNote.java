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

/**
 * Ghi chú/bằng chứng buổi tập (UC-048) — PT/Gym ghi nhận tiến độ, làm căn cứ
 * cho theo dõi luyện tập (UC-051) và giải quyết tranh chấp (UC-064).
 * Append-only; tác giả/thời điểm lấy từ BaseEntity.
 * Schema: session_notes(id, booking_id FK, note, evidence_url, + audit).
 */
@Entity
@Table(name = "session_notes", indexes =
        @Index(name = "idx_session_notes_booking", columnList = "booking_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionNote extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(nullable = false, length = 2000)
    private String note;

    /** URL ảnh/tài liệu bằng chứng (upload qua /api/files). */
    @Column(name = "evidence_url", length = 500)
    private String evidenceUrl;
}
