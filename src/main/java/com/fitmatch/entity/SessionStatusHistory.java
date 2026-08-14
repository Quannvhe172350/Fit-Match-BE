package com.fitmatch.entity;

import com.fitmatch.common.enums.SessionStatus;
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Lịch sử của một ngày tập. Tách khỏi {@link TicketStatusHistory} vì vé và buổi
 * là hai state machine độc lập. Dời lịch, đổi khung PT, thêm/bỏ PT ghi ở đây
 * dưới dạng dòng from == to.
 */
@Entity
@Table(name = "session_status_history", indexes =
        @Index(name = "idx_ssh_session", columnList = "session_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionStatusHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private TrainingSession session;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private SessionStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private SessionStatus toStatus;

    @Column(length = 500)
    private String reason;
}
