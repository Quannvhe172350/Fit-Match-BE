package com.fitmatch.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Chính sách vận hành của Gym (UC-017): đặt lịch, hủy, no-show, nội quy.
 * Quan hệ 1-1 với {@link GymProfile}.
 * Schema: gym_policies(id, gym_profile_id FK UNIQUE, booking_policy,
 * cancellation_policy, no_show_policy, house_rules, + audit).
 */
@Entity
@Table(name = "gym_policies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GymPolicy extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gym_profile_id", nullable = false, unique = true)
    private GymProfile gymProfile;

    @Column(name = "booking_policy", length = 2000)
    private String bookingPolicy;

    @Column(name = "cancellation_policy", length = 2000)
    private String cancellationPolicy;

    @Column(name = "no_show_policy", length = 2000)
    private String noShowPolicy;

    @Column(name = "house_rules", length = 2000)
    private String houseRules;
}
