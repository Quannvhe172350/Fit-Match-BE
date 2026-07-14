package com.fitmatch.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

/**
 * Principal kèm tokenVersion (UC-003/004) — JwtAuthFilter so khớp với claim
 * "ver" trong JWT để từ chối token phát hành trước lần logout/đổi mật khẩu gần nhất.
 */
@Getter
public class AuthUserDetails extends org.springframework.security.core.userdetails.User {

    private final int tokenVersion;

    public AuthUserDetails(String username, String passwordHash,
                           Collection<? extends GrantedAuthority> authorities, int tokenVersion) {
        super(username, passwordHash, authorities);
        this.tokenVersion = tokenVersion;
    }
}
