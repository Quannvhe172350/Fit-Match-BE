package com.fitmatch.exception;

import com.fitmatch.common.enums.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** P2: các handler mới trả mã đúng và KHÔNG lộ chi tiết nội bộ ra client. */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private HttpServletRequest req() {
        HttpServletRequest r = mock(HttpServletRequest.class);
        when(r.getRequestURI()).thenReturn("/api/x");
        return r;
    }

    @Test
    void dataIntegrityViolation_returns409_withoutLeakingConstraint() {
        var ex = new DataIntegrityViolationException("Duplicate entry 'a@b.com' for key 'uk_users_email'");

        ResponseEntity<ErrorResponse> res = handler.handleDataIntegrity(ex, req());

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(res.getBody().getCode()).isEqualTo(ErrorCode.INVALID_STATE.name());
        // Không lộ tên constraint/SQL.
        assertThat(res.getBody().getMessage()).doesNotContain("uk_users_email");
    }

    @Test
    void typeMismatch_returns400() {
        var ex = new MethodArgumentTypeMismatchException("abc", Long.class, "id", null, null);

        ResponseEntity<ErrorResponse> res = handler.handleTypeMismatch(ex, req());

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR.name());
    }

    @Test
    void fallback_returnsGenericMessage_notLeakingException() {
        var ex = new RuntimeException("NullPointer at SecretService line 42");

        ResponseEntity<ErrorResponse> res = handler.handleException(ex, req());

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(res.getBody().getMessage()).isEqualTo(ErrorCode.INTERNAL_ERROR.getDefaultMessage());
        assertThat(res.getBody().getMessage()).doesNotContain("SecretService");
    }
}
