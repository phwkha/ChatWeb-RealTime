package com.web.backend.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.controller.response.ApiResponse;
import com.web.backend.exception.custom.AccessForbiddenException;
import com.web.backend.exception.custom.AuthenticationFailedException;
import com.web.backend.exception.custom.DuplicateRequestException;
import com.web.backend.exception.custom.InvalidDataException;
import com.web.backend.exception.custom.InvalidOtpException;
import com.web.backend.exception.custom.InvalidPasswordException;
import com.web.backend.exception.custom.PasswordMismatchException;
import com.web.backend.exception.custom.ResourceConflictException;
import com.web.backend.exception.custom.ResourceNotFoundException;
import com.web.backend.exception.custom.SystemOverloadException;
import com.web.backend.exception.custom.TooManyRequestsException;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.persistence.OptimisticLockException;
import jakarta.validation.ConstraintViolationException;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @InjectMocks
    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messageSource = mock(ResourceBundleMessageSource.class);
        lenient().when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Handled Exception Message");
        Translator.setStaticMessageSource(messageSource);
    }

    @Test
    void testHandleDisabledException() {
        ApiResponse<Void> response = handler.handleDisabledException(new DisabledException("Account disabled"));
        assertEquals(HttpStatus.FORBIDDEN.value(), response.getCode());
    }

    @Test
    void testHandleLockedException() {
        ApiResponse<Void> response = handler.handleLockedException(new LockedException("Account locked"));
        assertEquals(HttpStatus.FORBIDDEN.value(), response.getCode());
    }

    @Test
    void testHandleHttpRequestMethodNotSupportedException() {
        ApiResponse<Void> response = handler.handleHttpRequestMethodNotSupportedException(
                new HttpRequestMethodNotSupportedException("POST"));
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED.value(), response.getCode());
    }

    @Test
    void testHandleMissingServletRequestParameterException() {
        ApiResponse<Void> response = handler.handleMissingServletRequestParameterException(
                new MissingServletRequestParameterException("param1", "String"));
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getCode());
    }

    @Test
    void testHandleMethodArgumentTypeMismatchException() {
        MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
        lenient().when(ex.getName()).thenReturn("id");
        ApiResponse<Void> response = handler.handleMethodArgumentTypeMismatchException(ex);
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getCode());
    }

    @Test
    void testHandleHttpMessageNotReadableException() {
        ApiResponse<Void> response = handler.handleHttpMessageNotReadableException(
                new HttpMessageNotReadableException("Malformed body"));
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getCode());
    }

    @Test
    void testHandleDataIntegrityViolationException() {
        ApiResponse<Void> response = handler.handleDataIntegrityViolationException(
                new DataIntegrityViolationException("Unique constraint violation"));
        assertEquals(HttpStatus.CONFLICT.value(), response.getCode());
    }

    @Test
    void testHandleOptimisticLockException() {
        ApiResponse<Void> response = handler.handleOptimisticLockException(
                new OptimisticLockException("Row updated by another transaction"));
        assertEquals(HttpStatus.CONFLICT.value(), response.getCode());
    }

    @Test
    void testHandleSpringAccessDeniedException() {
        ApiResponse<Void> response = handler.handleSpringAccessDeniedException(
                new AccessDeniedException("Access is denied"));
        assertEquals(HttpStatus.FORBIDDEN.value(), response.getCode());
    }

    @Test
    void testHandleAuthenticationFailedException() {
        ApiResponse<Void> response = handler.handleAuthenticationFailedException(
                new AuthenticationFailedException("Bad credentials"));
        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getCode());
    }

    @Test
    void testHandlePasswordMismatchException() {
        ApiResponse<Void> response = handler.handlePasswordMismatchException(
                new PasswordMismatchException("Passwords do not match"));
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getCode());
    }

    @Test
    void testHandleInvalidPasswordException() {
        ApiResponse<Void> response = handler.handleInvalidPasswordException(
                new InvalidPasswordException("Invalid current password"));
        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getCode());
    }

    @Test
    void testHandleInvalidOtpException() {
        ApiResponse<Void> response = handler.handleInvalidOtpException(
                new InvalidOtpException("Invalid OTP code"));
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getCode());
    }

    @Test
    void testHandleInvalidDataException() {
        ApiResponse<Void> response = handler.handleInvalidDataException(
                new InvalidDataException("Invalid input data"));
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getCode());
    }

    @Test
    void testHandleResourceNotFoundException() {
        ApiResponse<Void> response = handler.handleResourceNotFoundException(
                new ResourceNotFoundException("User not found"));
        assertEquals(HttpStatus.NOT_FOUND.value(), response.getCode());
    }

    @Test
    void testHandleResourceConflictException() {
        ApiResponse<Void> response = handler.handleResourceConflictException(
                new ResourceConflictException("Email already exists"));
        assertEquals(HttpStatus.CONFLICT.value(), response.getCode());
    }

    @Test
    void testHandleAccessForbiddenException() {
        ApiResponse<Void> response = handler.handleAccessForbiddenException(
                new AccessForbiddenException("Forbidden action"));
        assertEquals(HttpStatus.FORBIDDEN.value(), response.getCode());
    }

    @Test
    void testHandleExpiredJwtException() {
        ApiResponse<Void> response = handler.handleExpiredJwtException(mock(ExpiredJwtException.class));
        assertEquals(4011, response.getCode());
    }

    @Test
    void testHandleJwtException() {
        ApiResponse<Void> response = handler.handleJwtException(new JwtException("Signature invalid"));
        assertEquals(4012, response.getCode());
    }

    @Test
    void testHandleMaxSizeException() {
        ApiResponse<Void> response = handler.handleMaxSizeException(new MaxUploadSizeExceededException(50000000));
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE.value(), response.getCode());
    }

    @Test
    void testHandleConstraintViolationException() {
        ApiResponse<Void> response = handler.handleConstraintViolationException(
                new ConstraintViolationException("Invalid field value", null));
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getCode());
    }

    @Test
    void testHandleSystemOverloadException() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleSystemOverloadException(
                new SystemOverloadException("High traffic queue full"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
    }

    @Test
    void testHandleTooManyRequestsException() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleTooManyRequestsException(
                new TooManyRequestsException("error.rate_limit", 15));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertEquals("15", response.getHeaders().getFirst("Retry-After"));
    }

    @Test
    void testHandleDuplicateRequestException() {
        ApiResponse<Void> response = handler.handleDuplicateRequestException(
                new DuplicateRequestException("error.duplicate_req"));
        assertEquals(HttpStatus.CONFLICT.value(), response.getCode());
    }

    @Test
    void testHandleException_InternalServerError() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleException(
                new RuntimeException("Database connection timeout"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
    }
}
