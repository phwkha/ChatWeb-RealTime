package com.web.backend.exception;

import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ControllerAdvice;

import com.web.backend.common.ErrorCode;
import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.controller.response.ErrorSocketResponse;
import com.web.backend.exception.custom.AccessForbiddenException;
import com.web.backend.exception.custom.AuthenticationFailedException;
import com.web.backend.exception.custom.InvalidDataException;
import com.web.backend.exception.custom.ResourceConflictException;
import com.web.backend.exception.custom.ResourceNotFoundException;
import com.web.backend.exception.custom.SystemOverloadException;
import com.web.backend.exception.custom.TooManyRequestsException;
import com.web.backend.service.WebSocketRoutingService;

import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@ControllerAdvice
@RequiredArgsConstructor
@Slf4j(topic = "WEBSOCKET-ERROR-HANDLE")
public class WebSocketErrorHandler {

    private final WebSocketRoutingService webSocketRoutingService;

    private static final String QUEUE_ERRORS_STRING = "/queue/errors";

    private static final String ERROR_WS_INVALID_DATA_STRING = "error.ws.invalid_data";
    private static final String ERROR_SYS_BAD_FORMAT_STRING = "error.sys.bad_format";
    private static final String ERROR_SYS_BUSY_STRING = "error.sys.busy";

    public void handleChatError(String username, Object request, String message) {
        handleChatError(username, ErrorCode.SYSTEM_OVERLOAD, request, message);
    }

    public void handleChatError(String username, ErrorCode errorCode, Object request, String message) {
        try {
            webSocketRoutingService.routeMessage(username,
                    QUEUE_ERRORS_STRING,
                    ErrorSocketResponse.builder()
                            .code(errorCode.getHttpStatus())
                            .errorCode(errorCode)
                            .message(message)
                            .request(request)
                            .build());
            log.debug("Dispatched WebSocket error to user '{}': [{} - {}] {}", username, errorCode.getHttpStatus(),
                    errorCode, message);
        } catch (Exception e) {
            log.error("Push error message to user '{}' failed: {}", username, message, e);
        }
    }

    public void handleChatError(Authentication authentication, String sessionId, ErrorCode errorCode,
            Object request, String message) {
        ErrorSocketResponse errorResponse = ErrorSocketResponse.builder()
                .code(errorCode.getHttpStatus())
                .errorCode(errorCode)
                .message(message)
                .request(request)
                .build();
        if (authentication != null && authentication.getName() != null) {
            try {
                webSocketRoutingService.routeMessage(authentication.getName(), QUEUE_ERRORS_STRING, errorResponse);
                log.debug("Dispatched WebSocket error to user '{}': [{} - {}] {}", authentication.getName(),
                        errorCode.getHttpStatus(), errorCode, message);
            } catch (Exception e) {
                log.error("Push error message to user '{}' failed: {}", authentication.getName(), message, e);
            }
        } else if (sessionId != null) {
            webSocketRoutingService.routeMessageToSession(sessionId, QUEUE_ERRORS_STRING, errorResponse);
        } else {
            log.warn("Unable to route WebSocket error (both authentication and sessionId are null): {}", message);
        }
    }

    @MessageExceptionHandler(MethodArgumentNotValidException.class)
    public void handleWebSocketValidationException(
            MethodArgumentNotValidException ex,
            Authentication authentication,
            @Header(value = "simpSessionId", required = false) String sessionId) {

        String errorMessage = Translator.tolocale(ERROR_WS_INVALID_DATA_STRING);
        Object requestData = null;

        BindingResult bindingResult = ex.getBindingResult();
        if (bindingResult != null) {
            requestData = bindingResult.getTarget();
            FieldError fieldError = bindingResult.getFieldError();
            if (fieldError != null && fieldError.getDefaultMessage() != null) {
                errorMessage = fieldError.getDefaultMessage();
            }
        }

        this.handleChatError(authentication, sessionId, ErrorCode.INVALID_INPUT, requestData, errorMessage);
    }

    @MessageExceptionHandler(MessageConversionException.class)
    public void handleMessageConversionException(
            MessageConversionException ex,
            Authentication authentication,
            @Header(value = "simpSessionId", required = false) String sessionId) {

        String errorMessage = Translator.tolocale(ERROR_SYS_BAD_FORMAT_STRING);

        this.handleChatError(authentication, sessionId, ErrorCode.BAD_FORMAT, null, errorMessage);
    }

    @MessageExceptionHandler(AccessForbiddenException.class)
    public void handleAccessForbiddenException(
            AccessForbiddenException ex,
            Authentication authentication,
            @Header(value = "simpSessionId", required = false) String sessionId) {
        this.handleChatError(authentication, sessionId, ErrorCode.ACCESS_FORBIDDEN, ex.getRequestData(),
                ex.getMessage());
    }

    @MessageExceptionHandler(InvalidDataException.class)
    public void handleInvalidDataException(
            InvalidDataException ex,
            Authentication authentication,
            @Header(value = "simpSessionId", required = false) String sessionId) {
        this.handleChatError(authentication, sessionId, ErrorCode.INVALID_DATA, ex.getRequestData(),
                ex.getMessage());
    }

    @MessageExceptionHandler(ResourceNotFoundException.class)
    public void handleResourceNotFoundException(
            ResourceNotFoundException ex,
            Authentication authentication,
            @Header(value = "simpSessionId", required = false) String sessionId) {
        this.handleChatError(authentication, sessionId, ErrorCode.RESOURCE_NOT_FOUND, ex.getRequestData(),
                ex.getMessage());
    }

    @MessageExceptionHandler(ResourceConflictException.class)
    public void handleResourceConflictException(
            ResourceConflictException ex,
            Authentication authentication,
            @Header(value = "simpSessionId", required = false) String sessionId) {
        this.handleChatError(authentication, sessionId, ErrorCode.RESOURCE_CONFLICT, ex.getRequestData(),
                ex.getMessage());
    }

    @MessageExceptionHandler(AccessDeniedException.class)
    public void handleAccessDeniedException(
            AccessDeniedException ex,
            Authentication authentication,
            @Header(value = "simpSessionId", required = false) String sessionId) {

        this.handleChatError(authentication, sessionId, ErrorCode.ACCESS_DENIED, null, ex.getMessage());
    }

    @MessageExceptionHandler(SystemOverloadException.class)
    public void handleSystemOverloadException(
            SystemOverloadException ex,
            Authentication authentication,
            @Header(value = "simpSessionId", required = false) String sessionId) {
        this.handleChatError(authentication, sessionId, ErrorCode.SYSTEM_OVERLOAD, ex.getRequestData(),
                ex.getMessage());
    }

    @MessageExceptionHandler(TooManyRequestsException.class)
    public void handleTooManyRequestsException(
            TooManyRequestsException ex,
            Authentication authentication,
            @Header(value = "simpSessionId", required = false) String sessionId) {
        String message = Translator.tolocale(ex.getMessageKey());
        this.handleChatError(authentication, sessionId, ErrorCode.RATE_LIMITED, null, message);
    }

    @MessageExceptionHandler(AuthenticationFailedException.class)
    public void handleAuthenticationFailedException(
            AuthenticationFailedException ex,
            Authentication authentication,
            @Header(value = "simpSessionId", required = false) String sessionId) {
        this.handleChatError(authentication, sessionId, ErrorCode.UNAUTHORIZED, null, ex.getMessage());
    }

    @MessageExceptionHandler(ConstraintViolationException.class)
    public void handleConstraintViolationException(
            ConstraintViolationException ex,
            Authentication authentication,
            @Header(value = "simpSessionId", required = false) String sessionId) {
        String errorMessage = Translator.tolocale(ERROR_WS_INVALID_DATA_STRING);
        this.handleChatError(authentication, sessionId, ErrorCode.CONSTRAINT_VIOLATION, null, errorMessage);
    }

    @MessageExceptionHandler(IllegalArgumentException.class)
    public void handleIllegalArgumentException(
            IllegalArgumentException ex,
            Authentication authentication,
            @Header(value = "simpSessionId", required = false) String sessionId) {
        this.handleChatError(authentication, sessionId, ErrorCode.ILLEGAL_ARGUMENT, null, ex.getMessage());
    }

    @MessageExceptionHandler(IllegalStateException.class)
    public void handleIllegalStateException(
            IllegalStateException ex,
            Authentication authentication,
            @Header(value = "simpSessionId", required = false) String sessionId) {
        this.handleChatError(authentication, sessionId, ErrorCode.ILLEGAL_STATE, null, ex.getMessage());
    }

    @MessageExceptionHandler(Exception.class)
    public void handleAllOtherExceptions(
            Exception ex,
            Authentication authentication,
            @Header(value = "simpSessionId", required = false) String sessionId) {
        log.error("Unhandled WebSocket exception: ", ex);
        String errorMessage = Translator.tolocale(ERROR_SYS_BUSY_STRING);

        this.handleChatError(authentication, sessionId, ErrorCode.INTERNAL_SERVER_ERROR, null, errorMessage);
    }
}
