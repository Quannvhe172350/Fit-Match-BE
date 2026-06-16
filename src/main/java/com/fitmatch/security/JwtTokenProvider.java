package com.fitmatch.security;

import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.exception.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;

@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey accessTokenKey;
    private final SecretKey refreshTokenKey;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        byte[] keyBytes = Base64.getDecoder().decode(jwtProperties.getSecret());
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT secret must be at least 256 bits (32 bytes) when Base64-decoded");
        }
        this.accessTokenKey = Keys.hmacShaKeyFor(keyBytes);
        this.refreshTokenKey = Keys.hmacShaKeyFor(("refresh-" + jwtProperties.getSecret()).getBytes());
        this.accessTokenExpiration = jwtProperties.getAccessTokenExpiration();
        this.refreshTokenExpiration = jwtProperties.getRefreshTokenExpiration();
    }

    public String generateAccessToken(String username, String role) {
        return buildToken(username, role, accessTokenExpiration, accessTokenKey);
    }

    public String generateRefreshToken(String username) {
        return buildToken(username, null, refreshTokenExpiration, refreshTokenKey);
    }

    public String getUsernameFromAccessToken(String token) {
        return parseClaims(token, accessTokenKey).getSubject();
    }

    public String getRoleFromAccessToken(String token) {
        Claims claims = parseClaims(token, accessTokenKey);
        return claims.get("role", String.class);
    }

    public String getUsernameFromRefreshToken(String token) {
        return parseClaims(token, refreshTokenKey).getSubject();
    }

    public boolean validateAccessToken(String token) {
        try {
            parseClaims(token, accessTokenKey);
            return true;
        } catch (ExpiredJwtException e) {
            log.debug("Access token expired for subject: {}", e.getClaims().getSubject());
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid access token: {}", e.getMessage());
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
    }

    public boolean validateRefreshToken(String token) {
        try {
            parseClaims(token, refreshTokenKey);
            return true;
        } catch (ExpiredJwtException e) {
            log.debug("Refresh token expired for subject: {}", e.getClaims().getSubject());
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid refresh token: {}", e.getMessage());
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
    }

    public long getAccessTokenExpiration() {
        return accessTokenExpiration;
    }

    private String buildToken(String subject, String role, long expiration, SecretKey key) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        var builder = Jwts.builder()
                .subject(subject)
                .issuedAt(now)
                .expiration(expiryDate);

        if (role != null) {
            builder.claim("role", role);
        }

        return builder.signWith(key).compact();
    }

    private Claims parseClaims(String token, SecretKey key) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
