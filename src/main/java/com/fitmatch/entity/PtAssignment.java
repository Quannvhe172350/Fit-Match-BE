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

/**
 * Câu 24: gán PT vào CHI NHÁNH của Gym — đích duy nhất còn lại.
 *
 * <p>Mô hình cũ có thêm hai đích dịch vụ/gói tập, nhưng mô hình vé không còn
 * hai thứ đó. {@code PtSlotValidator} chỉ hỏi đúng một câu: PT này có phụ
 * trách chi nhánh của vé không. Xem V78__pt_assignments_branch_only.sql.
 */
@Entity
@Table(name = "pt_assignments", uniqueConstraints =
        @UniqueConstraint(name = "uk_pt_assignment_branch", columnNames = {"pt_profile_id", "gym_branch_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PtAssignment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pt_profile_id", nullable = false)
    private PtProfile ptProfile;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gym_branch_id", nullable = false)
    private GymBranch gymBranch;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
