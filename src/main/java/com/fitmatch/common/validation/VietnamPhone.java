package com.fitmatch.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Số điện thoại Việt Nam (UC-016).
 *
 * <p>Tách thành annotation riêng thay vì rắc {@code @Pattern} khắp nơi: quy tắc
 * đầu số của Việt Nam có đổi (đợt chuyển di động 11 -&gt; 10 số năm 2018), và một
 * regex bị chép ra nhiều DTO thì lần sửa sau chắc chắn sót chỗ.
 *
 * <p>Null được bỏ qua để {@code @NotBlank} lo phần "bắt buộc" — nếu không, một ô
 * để trống sẽ nhận hai thông báo lỗi cùng lúc.
 */
@Documented
@Constraint(validatedBy = VietnamPhoneValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface VietnamPhone {

    String message() default
            "Số điện thoại không hợp lệ. Dùng số Việt Nam, ví dụ 0901234567 hoặc 02838221234";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
