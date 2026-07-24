package com.fitmatch.common.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** P1-1.6: chính sách mật khẩu — 8-100 ký tự, có ít nhất một chữ và một số. */
class StrongPasswordValidatorTest {

    private final StrongPasswordValidator validator = new StrongPasswordValidator();

    @Test
    void accepts_letterAndDigit_minLength() {
        assertThat(validator.isValid("abc12345", null)).isTrue();
    }

    @Test
    void rejects_tooShort() {
        assertThat(validator.isValid("ab12", null)).isFalse();
    }

    @Test
    void rejects_noDigit() {
        assertThat(validator.isValid("abcdefgh", null)).isFalse();
    }

    @Test
    void rejects_noLetter() {
        assertThat(validator.isValid("12345678", null)).isFalse();
    }

    @Test
    void nullDelegatedToNotBlank() {
        // @NotBlank xử lý null/blank riêng -> validator trả true để không nhân đôi lỗi.
        assertThat(validator.isValid(null, null)).isTrue();
    }
}
