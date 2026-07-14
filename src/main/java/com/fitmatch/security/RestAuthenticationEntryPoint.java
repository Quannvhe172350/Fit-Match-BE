package com.fitmatch.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Chuẩn hoá lỗi 401 (UC-003): trả JSON ErrorResponse với mã cụ thể
 * (TOKEN_EXPIRED / TOKEN_INVALID từ JwtAuthFilter, UNAUTHORIZED nếu thiếu token)
 * để FE phân biệt được "hết hạn -> refresh" và "không hợp lệ -> đăng nhập lại".
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        Object attr = request.getAttribute("jwt.error");
        ErrorCode code = attr instanceof ErrorCode ec ? ec : ErrorCode.UNAUTHORIZED;
        write(response, request, code);
    }

    private void write(HttpServletResponse response, HttpServletRequest request, ErrorCode code)
            throws IOException {
        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(code.getStatus().value())
                .code(code.name())
                .message(code.getDefaultMessage())
                .path(request.getRequestURI())
                .build());
    }
}
