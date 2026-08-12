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
 * Rate limit in-memory theo IP cho các endpoint nhạy cảm: auth (UC-003/004 —
 * chống brute-force mật khẩu và spam email) và proxy geocode công khai (UC-18 —
 * chống đốt quota Google). Fixed window 1 phút, đơn giản đủ dùng cho 1 instance;
 * khi scale ngang thay bằng bucket phân tán (Redis).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthRateLimitFilter extends OncePerRequestFilter {

    /** Giới hạn request/phút/IP, khoá theo "METHOD path". */
    // Map.ofEntries (không phải Map.of) vì Map.of chỉ nhận tối đa 10 cặp.
    private static final Map<String, Integer> LIMITS = Map.ofEntries(
            Map.entry("POST /api/auth/login", 10),
            // UC-003: đăng nhập Google không dò được mật khẩu, nhưng vẫn giới hạn để
            // không ai spam BE gọi Google xác minh token rác.
            Map.entry("POST /api/auth/google", 10),
            Map.entry("POST /api/auth/register", 10),
            Map.entry("POST /api/auth/refresh", 30),
            Map.entry("POST /api/auth/forgot-password", 5),
            Map.entry("POST /api/auth/resend-verification", 5),
            Map.entry("POST /api/auth/reset-password", 10),
            // UC-18 (V55): proxy geocode công khai gọi Google bằng key của nền tảng —
            // không giới hạn thì một script có thể đốt sạch quota trong vài phút.
            // Hạn mức rộng tay vì người dùng thật gõ địa chỉ có debounce.
            Map.entry("GET /api/marketplace/geocode", 30),
            Map.entry("GET /api/marketplace/geocode/reverse", 30),
            // V65: gợi ý địa điểm bị gọi theo NHỊP GÕ nên hạn mức phải rộng hơn hẳn
            // hai endpoint trên. FE debounce 350ms, một phiên nhập địa chỉ thật tốn
            // chừng 3–6 lượt; 90 là đủ thoải mái mà vẫn chặn được script quét.
            Map.entry("GET /api/marketplace/geocode/autocomplete", 90)
    );
    private static final long WINDOW_MS = 60_000;

    private final ObjectMapper objectMapper;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String route = request.getMethod().toUpperCase() + " " + request.getRequestURI();
        Integer limit = LIMITS.get(route);
        if (limit == null) {
            filterChain.doFilter(request, response);
            return;
        }

        long now = System.currentTimeMillis();
        String key = clientIp(request) + "|" + route;
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
        // P1-12: KHÔNG tin X-Forwarded-For do client tự gửi — nếu tin, attacker đổi
        // header mỗi request để né rate limit. Dùng IP socket thật. Khi triển khai
        // sau reverse proxy, cấu hình server.forward-headers-strategy=framework|native
        // để getRemoteAddr() trả IP client thật một cách an toàn.
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
