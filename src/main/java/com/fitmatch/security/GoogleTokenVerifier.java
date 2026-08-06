package com.fitmatch.security;

import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.config.GoogleOAuthProperties;
import com.fitmatch.exception.BusinessException;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

/**
 * Xác minh ID token do Google phát hành cho luồng đăng nhập Google (UC-003).
 *
 * <p>Kiểm tra chữ ký bằng public key của Google (tự tải và cache theo header
 * Cache-Control), cùng các claim {@code iss}, {@code aud} và {@code exp}. Chỉ
 * chấp nhận audience nằm trong danh sách client id đã cấu hình — nếu không,
 * một ID token hợp lệ nhưng phát cho ứng dụng khác cũng đăng nhập được vào
 * FitMatch (lỗ hổng "confused deputy" kinh điển của Google Sign-In).
 *
 * <p>Không cấu hình client id -> mọi lời gọi trả 503 GOOGLE_AUTH_DISABLED.
 */
@Slf4j
@Component
public class GoogleTokenVerifier {

    private static final List<String> ISSUERS = List.of("https://accounts.google.com", "accounts.google.com");

    private final GoogleIdTokenVerifier verifier;

    public GoogleTokenVerifier(GoogleOAuthProperties properties) {
        List<String> clientIds = properties.getEffectiveClientIds();
        if (clientIds.isEmpty()) {
            log.info("Google sign-in disabled: app.google-oauth.client-ids is empty");
            this.verifier = null;
        } else {
            this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                    .setAudience(clientIds)
                    .setIssuers(ISSUERS)
                    .build();
            log.info("Google sign-in enabled for {} client id(s)", clientIds.size());
        }
    }

    public boolean isEnabled() {
        return verifier != null;
    }

    /**
     * @return payload đã xác minh (chắc chắn có {@code sub} và {@code email})
     * @throws BusinessException 503 nếu chưa cấu hình; 401 nếu token sai/hết hạn/
     *                           sai audience; 403 nếu email chưa được Google xác minh
     */
    public GoogleIdToken.Payload verify(String idToken) {
        if (verifier == null) {
            throw new BusinessException(ErrorCode.GOOGLE_AUTH_DISABLED);
        }

        GoogleIdToken token;
        try {
            token = verifier.verify(idToken);
        } catch (GeneralSecurityException e) {
            log.warn("Google ID token rejected: {}", e.getMessage());
            throw new BusinessException(ErrorCode.GOOGLE_TOKEN_INVALID);
        } catch (IOException e) {
            // Không lấy được public key của Google -> lỗi hạ tầng, KHÔNG phải token sai.
            log.error("Cannot fetch Google public keys: {}", e.getMessage());
            throw new BusinessException(ErrorCode.GOOGLE_AUTH_UNAVAILABLE);
        }

        if (token == null) {
            log.warn("Google ID token invalid (signature/audience/expiry check failed)");
            throw new BusinessException(ErrorCode.GOOGLE_TOKEN_INVALID);
        }

        GoogleIdToken.Payload payload = token.getPayload();
        if (payload.getEmail() == null || payload.getSubject() == null) {
            log.warn("Google ID token has no email/sub claim (missing 'email' scope?)");
            throw new BusinessException(ErrorCode.GOOGLE_TOKEN_INVALID,
                    "Google account did not provide an email address");
        }
        // Google chỉ đảm bảo email khi email_verified = true; tài khoản Workspace bị
        // thu hồi hoặc email phụ chưa xác minh thì không được coi là danh tính đã kiểm chứng.
        if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
            log.warn("Google account email is not verified: {}", payload.getEmail());
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED,
                    "This Google account does not have a verified email address");
        }
        return payload;
    }
}
