package com.fitmatch.common.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** UC-016: số điện thoại Việt Nam — di động 10 số, cố định 11 số. */
class VietnamPhoneValidatorTest {

    private final VietnamPhoneValidator validator = new VietnamPhoneValidator();

    @ParameterizedTest
    @ValueSource(strings = {
            "0901234567",       // di động 09
            "0387654321",       // di động 03 (đầu số sau đợt chuyển 11 -> 10 số)
            "0842345678",       // 084 là đầu số nội địa, KHÔNG phải +84 thiếu dấu
            "0987 654 321",     // khoảng trắng
            "0901.234.567",     // dấu chấm
            "02838221234",      // cố định TP.HCM
            "(028) 3822-1234",  // cố định có dấu ngoặc + gạch nối
            "+84901234567",     // quốc tế, bỏ số 0 đầu
            "+840901234567",    // quốc tế, giữ số 0 đầu
            "84901234567",      // quốc tế thiếu dấu +
            "0084901234567",    // quốc tế dạng 00
    })
    void accepts(String phone) {
        assertThat(validator.isValid(phone, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "0123456789",   // đầu số 01 đã bị thu hồi
            "0612345678",   // đầu số 06 không được cấp
            "090123456",    // thiếu một chữ số
            "09012345678",  // thừa một chữ số
            "0281234567",   // cố định phải 11 số, không phải 10
            "+12025550100", // số nước ngoài
            "0901234abc",   // lẫn chữ
            "khong-phai-so",
    })
    void rejects(String phone) {
        assertThat(validator.isValid(phone, null)).isFalse();
    }

    /**
     * Null/rỗng để {@code @NotBlank} lo — validator này báo thêm thì một ô để
     * trống nhận hai thông báo lỗi cùng lúc.
     */
    @Test
    void ignores_nullAndBlank() {
        assertThat(validator.isValid(null, null)).isTrue();
        assertThat(validator.isValid("   ", null)).isTrue();
    }
}
