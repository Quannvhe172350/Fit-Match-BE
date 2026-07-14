package com.fitmatch.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

/** Chuẩn hoá 403 khi đã đăng nhập nhưng không đủ quyền (RBAC). */
@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(ErrorCode.FORBIDDEN.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(ErrorCode.FORBIDDEN.getStatus().value())
                .code(ErrorCode.FORBIDDEN.name())
                .message("Access denied")
                .path(request.getRequestURI())
                .build());
    }
}
