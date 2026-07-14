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
        // Khoá refresh dẫn xuất từ key bytes đã decode (SHA-256(keyBytes || "refresh"))
        // — cùng entropy với access key, không dùng chuỗi Base64 thô.
        this.refreshTokenKey = Keys.hmacShaKeyFor(deriveRefreshKey(keyBytes));
        this.accessTokenExpiration = jwtProperties.getAccessTokenExpiration();
        this.refreshTokenExpiration = jwtProperties.getRefreshTokenExpiration();
    }

    private static byte[] deriveRefreshKey(byte[] keyBytes) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            digest.update(keyBytes);
            digest.update("refresh".getBytes());
            return digest.digest();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public String generateAccessToken(String username, String role, int tokenVersion) {
        return buildToken(username, role, tokenVersion, accessTokenExpiration, accessTokenKey);
    }

    public String generateRefreshToken(String username, int tokenVersion) {
        return buildToken(username, null, tokenVersion, refreshTokenExpiration, refreshTokenKey);
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

    /** Phiên bản token trong claim "ver" — token cũ (trước khi có claim) trả 0. */
    public int getVersionFromAccessToken(String token) {
        Integer ver = parseClaims(token, accessTokenKey).get("ver", Integer.class);
        return ver != null ? ver : 0;
    }

    public int getVersionFromRefreshToken(String token) {
        Integer ver = parseClaims(token, refreshTokenKey).get("ver", Integer.class);
        return ver != null ? ver : 0;
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

    private String buildToken(String subject, String role, int tokenVersion, long expiration, SecretKey key) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        var builder = Jwts.builder()
                .subject(subject)
                .issuedAt(now)
                .expiration(expiryDate)
                .claim("ver", tokenVersion);

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
