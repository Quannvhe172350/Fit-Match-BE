package com.fitmatch.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.exception.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rate limit in-memory theo IP cho các endpoint auth nhạy cảm (UC-003/004):
 * chống brute-force mật khẩu và spam gửi email. Fixed window 1 phút, đơn giản
 * đủ dùng cho 1 instance; khi scale ngang thay bằng bucket phân tán (Redis).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthRateLimitFilter extends OncePerRequestFilter {

    /** Giới hạn request/phút/IP theo path. */
    private static final Map<String, Integer> LIMITS = Map.of(
            "/api/auth/login", 10,
            "/api/auth/register", 10,
            "/api/auth/refresh", 30,
            "/api/auth/forgot-password", 5,
            "/api/auth/resend-verification", 5,
            "/api/auth/reset-password", 10
    );
    private static final long WINDOW_MS = 60_000;

    private final ObjectMapper objectMapper;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Integer limit = LIMITS.get(request.getRequestURI());
        if (limit == null || !"POST".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        long now = System.currentTimeMillis();
        String key = clientIp(request) + "|" + request.getRequestURI();
        Window w = windows.compute(key, (k, cur) ->
                cur == null || now - cur.startMs >= WINDOW_MS ? new Window(now) : cur);
        if (w.count.incrementAndGet() > limit) {
            log.warn("Rate limit exceeded: {} ({}/min)", key, limit);
            response.setStatus(ErrorCode.RATE_LIMITED.getStatus().value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getWriter(), ErrorResponse.builder()
                    .timestamp(LocalDateTime.now())
                    .status(ErrorCode.RATE_LIMITED.getStatus().value())
                    .code(ErrorCode.RATE_LIMITED.name())
                    .message(ErrorCode.RATE_LIMITED.getDefaultMessage())
                    .path(request.getRequestURI())
                    .build());
            return;
        }

        // Dọn rác cơ hội: window đã quá hạn lâu (giữ map không phình).
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(e -> now - e.getValue().startMs >= WINDOW_MS * 2);
        }

        filterChain.doFilter(request, response);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static final class Window {
        final long startMs;
        final AtomicInteger count = new AtomicInteger();

        Window(long startMs) {
            this.startMs = startMs;
        }
    }
}
