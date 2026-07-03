package com.fitmatch.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Loại tài khoản cho phép khi tự đăng ký (UC-001).
 * Chỉ Customer và Gym Operator được tự đăng ký; PT do Gym tạo (UC-019),
 * Admin/Moderator/Finance Admin do Admin gán (UC-077) — nhờ đó client
 * không bao giờ gửi thẳng Role để tránh leo thang đặc quyền.
 */
@Getter
@RequiredArgsConstructor
public enum AccountType {

    CUSTOMER(Role.ROLE_CUSTOMER),
    GYM_OPERATOR(Role.ROLE_GYM_OPERATOR);

    private final Role role;
}
