package com.fitmatch.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Cache raw request body cho webhook Casso V2 (HMAC-SHA256 verification).
 * <p>
 * Đọc toàn bộ body một lần, lưu vào attribute {@code RAW_BODY} để controller
 * dùng verify chữ ký, đồng thời wrap request bằng {@link CachedBodyRequestWrapper}
 * để Jackson có thể deserialize bình thường từ cached bytes.
 */
@Slf4j
@Component
public class WebhookBodyCachingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // Chỉ cache body cho webhook endpoint
        if (!request.getRequestURI().contains("/webhooks/")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Đọc raw body một lần duy nhất
        byte[] rawBytes = StreamUtils.copyToByteArray(request.getInputStream());
        String rawBody = new String(rawBytes, StandardCharsets.UTF_8);
        request.setAttribute("RAW_BODY", rawBody);

        log.debug("Cached webhook body ({} bytes)", rawBytes.length);

        // Wrap để InputStream có thể đọc lại (cho Jackson deserialize)
        CachedBodyRequestWrapper wrapper = new CachedBodyRequestWrapper(request, rawBytes);
        filterChain.doFilter(wrapper, response);
    }

    /**
     * HttpServletRequest wrapper trả về cached body thay vì InputStream gốc đã consumed.
     */
    private static class CachedBodyRequestWrapper extends HttpServletRequestWrapper {
        private final byte[] cachedBody;

        CachedBodyRequestWrapper(HttpServletRequest request, byte[] cachedBody) {
            super(request);
            this.cachedBody = cachedBody;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream bais = new ByteArrayInputStream(cachedBody);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return bais.read();
                }

                @Override
                public boolean isFinished() {
                    return bais.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(jakarta.servlet.ReadListener listener) {
                    // no-op: synchronous only
                }
            };
        }
    }
}
