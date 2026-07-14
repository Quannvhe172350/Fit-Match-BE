package com.fitmatch.common.enums;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error"),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Validation failed"),
    BUSINESS_ERROR(HttpStatus.BAD_REQUEST, "Business error"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Unauthorized"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Forbidden"),
    USERNAME_EXISTS(HttpStatus.CONFLICT, "Username already exists"),
    EMAIL_EXISTS(HttpStatus.CONFLICT, "Email already exists"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid username or password"),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Invalid token"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Token has expired"),
    VERIFICATION_TOKEN_INVALID(HttpStatus.BAD_REQUEST, "Verification token is invalid or has expired"),
    EMAIL_ALREADY_VERIFIED(HttpStatus.CONFLICT, "Email is already verified"),
    EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "Email address has not been verified"),
    PROFILE_EXISTS(HttpStatus.CONFLICT, "Profile already exists for this user"),
    INVALID_STATE(HttpStatus.CONFLICT, "Operation not allowed in the current state"),
    PAYMENT_ERROR(HttpStatus.BAD_REQUEST, "Payment processing error"),
    INSUFFICIENT_BALANCE(HttpStatus.CONFLICT, "Insufficient wallet balance"),
    DUPLICATE_WEBHOOK(HttpStatus.OK, "Webhook already processed"),
    LEGACY_ENDPOINT_DISABLED(HttpStatus.GONE,
            "This endpoint belongs to the removed PT self-registration model. PTs are managed by their Gym (POST /api/gym/pts)");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }
}
