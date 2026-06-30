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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Tài liệu xác minh do Gym nộp (UC-41). File biểu diễn bằng URL (D-08).
 * Schema: gym_documents(id, gym_profile_id FK, document_type, file_url, + audit).
 */
@Entity
@Table(name = "gym_documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GymDocument extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gym_profile_id", nullable = false)
    private GymProfile gymProfile;

    @Column(name = "document_type", nullable = false, length = 100)
    private String documentType;

    @Column(name = "file_url", nullable = false, length = 500)
    private String fileUrl;
}
