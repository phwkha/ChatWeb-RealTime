package com.web.backend.exception;

import java.util.HashMap;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.controller.response.ApiResponse;
import com.web.backend.exception.custom.AccessForbiddenException;
import com.web.backend.exception.custom.AuthenticationFailedException;
import com.web.backend.exception.custom.InvalidDataException;
import com.web.backend.exception.custom.InvalidOtpException;
import com.web.backend.exception.custom.InvalidPasswordException;
import com.web.backend.exception.custom.PasswordMismatchException;
import com.web.backend.exception.custom.ResourceConflictException;
import com.web.backend.exception.custom.ResourceNotFoundException;
import com.web.backend.exception.custom.SystemOverloadException;
import com.web.backend.exception.custom.TooManyRequestsException;
import org.springframework.http.HttpHeaders;

import jakarta.validation.ConstraintViolationException;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;

@Slf4j(topic = "GLOBAL-EXCEPTION-HANDLER")
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String STR_20_STRING = "20";

    private static final String ERROR_SYS_MISSING_PARAM_STRING = "error.sys.missing_param";
    private static final String ERROR_STORAGE_FILE_TOO_LARGE_STRING = "error.storage.file_too_large";
    private static final String ERROR_SYS_METHOD_STRING = "error.sys.method";
    private static final String ERROR_SYS_METHOD_NOT_SUPPORTED_STRING = "error.sys.method_not_supported";
    private static final String ERROR_SYS_PARAM_FORMAT_STRING = "error.sys.param_format";
    private static final String ERROR_SYS_BAD_FORMAT_STRING = "error.sys.bad_format";
    private static final String ERROR_SYS_CONFLICT_STRING = "error.sys.conflict";
    private static final String ERROR_SYS_INVALID_INPUT_STRING = "error.sys.invalid_input";

    private static final String ERROR_SYS_BUSY_STRING = "error.sys.busy";

    private static final String ERROR_AUTH_TOKEN_EXPIRED_STRING = "error.auth.token_expired";
    private static final String ERROR_AUTH_TOKEN_INVALID_STRING = "error.auth.token_invalid";

    @ExceptionHandler(DisabledException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Void> handleDisabledException(DisabledException ex) {
        log.warn("Access rejected: Account is disabled - {}", ex.getMessage());
        return ApiResponse.error(HttpStatus.FORBIDDEN.value(), ex.getMessage());
    }

    @ExceptionHandler(LockedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Void> handleLockedException(LockedException ex) {
        log.warn("Access rejected: Account is locked - {}", ex.getMessage());
        return ApiResponse.error(HttpStatus.FORBIDDEN.value(), ex.getMessage());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public ApiResponse<Void> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException ex) {
        log.warn("HTTP method not supported: {}", ex.getMessage());
        return ApiResponse.error(HttpStatus.METHOD_NOT_ALLOWED.value(),
                Translator.tolocale(ERROR_SYS_METHOD_STRING) + ex.getMethod()
                        + Translator.tolocale(ERROR_SYS_METHOD_NOT_SUPPORTED_STRING));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleMissingServletRequestParameterException(MissingServletRequestParameterException ex) {
        log.warn("Missing required request parameter: {}", ex.getParameterName());
        return ApiResponse.error(HttpStatus.BAD_REQUEST.value(),
                Translator.tolocale(ERROR_SYS_MISSING_PARAM_STRING, ex.getParameterName()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException ex) {
        log.warn("Method argument type mismatch: param='{}', message='{}'", ex.getName(), ex.getMessage());
        String message = String.format(Translator.tolocale(ERROR_SYS_PARAM_FORMAT_STRING), ex.getName());
        return ApiResponse.error(HttpStatus.BAD_REQUEST.value(), message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        log.warn("Malformed JSON request: {}", ex.getMessage());
        return ApiResponse.error(HttpStatus.BAD_REQUEST.value(), Translator.tolocale(ERROR_SYS_BAD_FORMAT_STRING));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
        log.error("Database integrity constraint violation: {}", ex.getMessage(), ex);
        return ApiResponse.error(HttpStatus.CONFLICT.value(), Translator.tolocale(ERROR_SYS_CONFLICT_STRING));
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Void> handleSpringAccessDeniedException(AccessDeniedException ex) {
        log.warn("Access denied (Spring Security): {}", ex.getMessage());
        return ApiResponse.error(HttpStatus.FORBIDDEN.value(), ex.getMessage());
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<Void> handleAuthenticationFailedException(AuthenticationFailedException ex) {
        log.warn("Authentication failed: {}", ex.getMessage());
        return ApiResponse.error(HttpStatus.UNAUTHORIZED.value(), ex.getMessage());
    }

    @ExceptionHandler(PasswordMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handlePasswordMismatchException(PasswordMismatchException ex) {
        log.warn("Password mismatch: {}", ex.getMessage());
        return ApiResponse.error(HttpStatus.BAD_REQUEST.value(), ex.getMessage());
    }

    @ExceptionHandler(InvalidPasswordException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<Void> handleInvalidPasswordException(InvalidPasswordException ex) {
        log.warn("Invalid password attempt: {}", ex.getMessage());
        return ApiResponse.error(HttpStatus.UNAUTHORIZED.value(), ex.getMessage());
    }

    @ExceptionHandler(InvalidOtpException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleInvalidOtpException(InvalidOtpException ex) {
        log.warn("OTP validation failed: {}", ex.getMessage());
        return ApiResponse.error(HttpStatus.BAD_REQUEST.value(), ex.getMessage());
    }

    @ExceptionHandler(InvalidDataException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleInvalidDataException(InvalidDataException ex) {
        log.warn("Invalid data error: {}", ex.getMessage());
        return ApiResponse.error(HttpStatus.BAD_REQUEST.value(), ex.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleResourceNotFoundException(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ApiResponse.error(HttpStatus.NOT_FOUND.value(), ex.getMessage());
    }

    @ExceptionHandler(ResourceConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleResourceConflictException(ResourceConflictException ex) {
        log.warn("Resource conflict: {}", ex.getMessage());
        return ApiResponse.error(HttpStatus.CONFLICT.value(), ex.getMessage());
    }

    @ExceptionHandler(AccessForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Void> handleAccessForbiddenException(AccessForbiddenException ex) {
        log.warn("Access forbidden: {}", ex.getMessage());
        return ApiResponse.error(HttpStatus.FORBIDDEN.value(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationExceptions(
            MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();

        BindingResult bindingResult = ex.getBindingResult();
        bindingResult.getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(HttpStatus.BAD_REQUEST.value(),
                        Translator.tolocale(ERROR_SYS_INVALID_INPUT_STRING),
                        errors));
    }

    @ExceptionHandler(ExpiredJwtException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<Void> handleExpiredJwtException(ExpiredJwtException ex) {
        log.warn("JWT token expired: {}", ex.getMessage());
        return ApiResponse.error(4011, Translator.tolocale(ERROR_AUTH_TOKEN_EXPIRED_STRING));
    }

    @ExceptionHandler(JwtException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<Void> handleJwtException(JwtException ex) {
        log.warn("JWT validation failed: {}", ex.getMessage());
        return ApiResponse.error(4012, Translator.tolocale(ERROR_AUTH_TOKEN_INVALID_STRING));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public ApiResponse<Void> handleMaxSizeException(MaxUploadSizeExceededException exc) {
        log.warn("Upload size exceeded limit: {}", exc.getMessage());
        return ApiResponse.error(HttpStatus.PAYLOAD_TOO_LARGE.value(),
                Translator.tolocale(ERROR_STORAGE_FILE_TOO_LARGE_STRING, STR_20_STRING));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleConstraintViolationException(ConstraintViolationException ex) {
        log.warn("Validation constraint violated: {}", ex.getMessage());
        return ApiResponse.error(HttpStatus.BAD_REQUEST.value(), Translator.tolocale(ERROR_SYS_INVALID_INPUT_STRING));
    }

    @ExceptionHandler(SystemOverloadException.class)
    public ResponseEntity<ApiResponse<Void>> handleSystemOverloadException(SystemOverloadException ex) {
        log.warn("System overload / processing delay: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(HttpStatus.SERVICE_UNAVAILABLE.value(), ex.getMessage()));
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ApiResponse<Void>> handleTooManyRequestsException(TooManyRequestsException ex) {
        log.warn("Rate limit exceeded: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(ApiResponse.error(HttpStatus.TOO_MANY_REQUESTS.value(),
                        Translator.tolocale(ex.getMessageKey())));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception ex) {
        log.error("Uncaught server exception occurred: ", ex);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, Translator.tolocale(ERROR_SYS_BUSY_STRING)));
    }
}