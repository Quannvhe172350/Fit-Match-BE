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

import java.math.BigDecimal;

/**
 * Một dịch vụ đã mua kèm vé (V82).
 *
 * <p>{@code name} và {@code price} là SNAPSHOT tại thời điểm mua, giống
 * {@code Ticket.unitPrice}: gym sửa giá dịch vụ ngày mai không được phép làm
 * đổi số tiền của vé đã bán — nếu đổi thì tổng vé sẽ lệch khỏi số tiền đã giữ
 * trong ví và sổ cái không còn giải thích được.
 */
@Entity
@Table(name = "ticket_service_items", indexes =
        @Index(name = "idx_tsi_ticket", columnList = "ticket_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketServiceItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    /** Giữ tham chiếu để đối soát; hiển thị thì dùng snapshot bên dưới. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gym_service_id", nullable = false)
    private GymService gymService;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;
}
