package com.fitmatch.exception;

import com.fitmatch.common.enums.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        ErrorCode errorCode = ex.getErrorCode();
        log.warn("Business exception: code={}, message={}, path={}", errorCode.name(), ex.getMessage(), request.getRequestURI());
        return build(errorCode, ex.getMessage(), request);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(ResourceNotFoundException ex, HttpServletRequest request) {
        log.warn("Resource not found: {}", ex.getMessage());
        return build(ErrorCode.RESOURCE_NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("Validation failed: {}", message);
        return build(ErrorCode.VALIDATION_ERROR, message, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.warn("Malformed request body on path {}: {}", request.getRequestURI(), ex.getMessage());
        return build(ErrorCode.VALIDATION_ERROR, "Malformed request body or invalid field value", request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentialsException(BadCredentialsException ex, HttpServletRequest request) {
        log.warn("Bad credentials: {}", ex.getMessage());
        return build(ErrorCode.INVALID_CREDENTIALS, ex.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied: {} on path: {}", ex.getMessage(), request.getRequestURI());
        return build(ErrorCode.FORBIDDEN, "Access denied", request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException ex, HttpServletRequest request) {
        log.warn("Authentication failed: {}", ex.getMessage());
        return build(ErrorCode.UNAUTHORIZED, "Authentication required", request);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(OptimisticLockingFailureException ex, HttpServletRequest request) {
        log.warn("Concurrent update conflict on path {}: {}", request.getRequestURI(), ex.getMessage());
        return build(ErrorCode.CONCURRENT_UPDATE, ErrorCode.CONCURRENT_UPDATE.getDefaultMessage(), request);
    }

    /**
     * P2: vi phạm ràng buộc DB (unique/FK) — vd đua đăng ký cùng email/username.
     * Trả 409 thay vì 500, và KHÔNG lộ tên constraint/câu SQL ra client.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Data integrity violation on path {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return build(ErrorCode.INVALID_STATE,
                "The request conflicts with existing data (duplicate or constraint violation)", request);
    }

    /** P2: sai kiểu tham số path/query (vd id không phải số) — 400 thay vì 500. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        log.warn("Argument type mismatch on path {}: parameter '{}'", request.getRequestURI(), ex.getName());
        return build(ErrorCode.VALIDATION_ERROR, "Invalid value for parameter '" + ex.getName() + "'", request);
    }

    /** P2: vi phạm @Validated trên tham số method (vd @RequestParam) — 400. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        log.warn("Constraint violation on path {}: {}", request.getRequestURI(), ex.getMessage());
        return build(ErrorCode.VALIDATION_ERROR, "One or more request parameters are invalid", request);
    }

    /**
     * P2: sort/filter theo field không tồn tại (vd guest ?sort=passwordHash) — 400
     * thay vì 500, và không lộ tên field/entity nội bộ.
     */
    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ErrorResponse> handlePropertyReference(PropertyReferenceException ex, HttpServletRequest request) {
        log.warn("Invalid sort/property reference on path {}: {}", request.getRequestURI(), ex.getMessage());
        return build(ErrorCode.VALIDATION_ERROR, "Invalid sort or filter field", request);
    }

    /**
     * BUG-12: gọi vào path API không tồn tại phải là 404, không phải 500.
     *
     * <p>Spring MVC không khớp được handler thì đẩy request sang resolver static
     * resource, chỗ đó ném {@link NoResourceFoundException}. Trước đây nó rơi vào
     * nhánh "Unexpected error" bên dưới nên client nhận 500 INTERNAL_ERROR và
     * tưởng server hỏng, trong khi thực tế chỉ là gõ sai đường dẫn hoặc endpoint
     * bị tắt theo profile (xem PaymentDevController).
     *
     * <p>{@link NoHandlerFoundException} là biến thể khi bật
     * {@code spring.mvc.throw-exception-if-no-handler-found} — bắt luôn cho chắc.
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ErrorResponse> handleNoHandler(Exception ex, HttpServletRequest request) {
        log.warn("No handler for {} {}", request.getMethod(), request.getRequestURI());
        return build(ErrorCode.RESOURCE_NOT_FOUND,
                "No endpoint " + request.getMethod() + " " + request.getRequestURI(), request);
    }

    /**
     * V64: file vượt hạn mức của tầng servlet bị chặn TRƯỚC khi vào controller, nên
     * kiểm tra dung lượng trong {@code ImageFileValidator} không kịp chạy. Không bắt
     * ở đây thì người dùng nhận 500 "Internal server error" khi chọn nhầm ảnh 40MB
     * từ điện thoại — hoàn toàn không hiểu chuyện gì xảy ra.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleUploadTooLarge(MaxUploadSizeExceededException ex,
                                                              HttpServletRequest request) {
        log.warn("Upload too large on path {}: {}", request.getRequestURI(), ex.getMessage());
        return build(ErrorCode.VALIDATION_ERROR,
                "The uploaded file is too large - please choose a smaller image", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception ex, HttpServletRequest request) {
        // Log đầy đủ phía server; client chỉ nhận thông báo chung — KHÔNG lộ
        // ex.getMessage() (có thể chứa tên constraint/SQL/field nội bộ).
        log.error("Unexpected error on path {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return build(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getDefaultMessage(), request);
    }

    private ResponseEntity<ErrorResponse> build(ErrorCode errorCode, String message, HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(errorCode.getStatus().value())
                .code(errorCode.name())
                .message(message != null ? message : errorCode.getDefaultMessage())
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(errorCode.getStatus()).body(body);
    }
}
