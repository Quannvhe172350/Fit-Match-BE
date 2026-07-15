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
 * Bằng chứng tranh chấp (UC-064) — mô tả + file (ảnh/tài liệu/session note).
 * Append-only; người gửi lấy từ BaseEntity.createdBy.
 * Schema: dispute_evidence(id, dispute_id FK, description, file_url, + audit).
 */
@Entity
@Table(name = "dispute_evidence", indexes =
        @Index(name = "idx_dispute_evidence_dispute", columnList = "dispute_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DisputeEvidence extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dispute_id", nullable = false)
    private Dispute dispute;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(name = "file_url", length = 500)
    private String fileUrl;
}
