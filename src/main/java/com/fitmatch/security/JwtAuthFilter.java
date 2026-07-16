package com.fitmatch.security;

import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.exception.BusinessException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String token = extractToken(request);
            if (StringUtils.hasText(token) && jwtTokenProvider.validateAccessToken(token)) {
                String username = jwtTokenProvider.getUsernameFromAccessToken(token);

                var userDetails = userDetailsService.loadUserByUsername(username);
                // UC-003/004: token phát hành trước lần logout/đổi mật khẩu gần nhất bị từ chối.
                if (userDetails instanceof AuthUserDetails auth
                        && auth.getTokenVersion() != jwtTokenProvider.getVersionFromAccessToken(token)) {
                    throw new BusinessException(ErrorCode.TOKEN_INVALID, "Token has been revoked");
                }
                // P1-10: quyền lấy từ DB (userDetails) — KHÔNG lấy role từ claim; đổi
                // role có hiệu lực ngay, không phải chờ token cũ hết hạn.
                var authentication = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities()
                );
                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );

                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("Authenticated user: {} with authorities: {}", username, userDetails.getAuthorities());
            }
        } catch (BusinessException e) {
            SecurityContextHolder.clearContext();
            request.setAttribute("jwt.error", e.getErrorCode());
        } catch (Exception e) {
            log.warn("Could not set authentication: {}", e.getMessage());
            SecurityContextHolder.clearContext();
            request.setAttribute("jwt.error", ErrorCode.TOKEN_INVALID);
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
