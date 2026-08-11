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
 * Tài khoản ngân hàng thụ hưởng của một user (V61) — dùng khi tạo lệnh rút.
 * Áp dụng cho cả Gym Operator, PT và khách hàng.
 * <p>
 * Lệnh rút vẫn snapshot lại số tài khoản/tên ngân hàng/chủ tài khoản tại thời
 * điểm tạo: sửa hay xoá tài khoản về sau không được làm sai lệnh đã gửi đi.
 */
@Entity
@Table(name = "bank_accounts", indexes =
        @Index(name = "idx_bank_account_user", columnList = "user_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankAccount extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bank_id", nullable = false)
    private Bank bank;

    @Column(name = "account_number", nullable = false, length = 50)
    private String accountNumber;

    /** Tên chủ tài khoản — admin đối chiếu trước khi chuyển khoản. */
    @Column(name = "account_holder", nullable = false, length = 150)
    private String accountHolder;

    /** Tài khoản chọn sẵn khi mở form rút tiền; mỗi user tối đa một cái. */
    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private boolean defaultAccount = false;
}
