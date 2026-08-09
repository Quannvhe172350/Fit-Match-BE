package com.fitmatch.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Master data ngân hàng Việt Nam (V61). {@code bin} là mã định danh 6 số dùng
 * trong chuẩn VietQR — không có nó thì không sinh được QR chuyển khoản cho
 * admin quét khi chi trả lệnh rút.
 * <p>
 * Trước đây FE giữ một mảng tên ngân hàng dạng chuỗi (banks.constant.ts), nên
 * số tài khoản người thụ hưởng chỉ hiển thị được chứ không dựng được QR.
 */
@Entity
@Table(name = "banks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Bank extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Mã BIN 6 số theo chuẩn NAPAS/VietQR (vd Vietcombank = 970436). */
    @Column(nullable = false, unique = true, length = 10)
    private String bin;

    /** Mã viết tắt quốc tế (VCB, TCB, ...) — tiện tra cứu và log. */
    @Column(nullable = false, unique = true, length = 20)
    private String code;

    /** Tên ngắn hiển thị trên form (Vietcombank, Techcombank, ...). */
    @Column(name = "short_name", nullable = false, length = 50)
    private String shortName;

    @Column(nullable = false, length = 200)
    private String name;

    /** Ngân hàng ngừng hỗ trợ thì tắt cờ này thay vì xoá — tài khoản cũ vẫn tra được. */
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
