package com.fitmatch.entity;

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

/**
 * Bảng nối: loại vé được bán ở chi nhánh nào (câu 20). Gym khai vé một lần rồi
 * tick chọn chi nhánh, thay vì tạo lại cùng một loại vé cho từng chi nhánh.
 * Service layer bảo đảm chi nhánh phải thuộc chính gym sở hữu loại vé.
 */
@Entity
@Table(name = "ticket_type_branches",
        uniqueConstraints = @UniqueConstraint(name = "uk_ttb_type_branch",
                columnNames = {"ticket_type_id", "gym_branch_id"}),
        indexes = @Index(name = "idx_ttb_branch", columnList = "gym_branch_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketTypeBranch extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_type_id", nullable = false)
    private TicketType ticketType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gym_branch_id", nullable = false)
    private GymBranch gymBranch;
}
