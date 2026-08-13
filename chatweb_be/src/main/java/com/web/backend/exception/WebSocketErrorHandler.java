package com.web.backend.exception;

import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.controller.response.ErrorSocketResponse;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.security.core.Authentication;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.validation.BindingResult;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.handler.annotation.Header;
import com.web.backend.exception.custom.AccessForbiddenException;
import com.web.backend.exception.custom.InvalidDataException;
import com.web.backend.exception.custom.ResourceNotFoundException;
import com.web.backend.exception.custom.ResourceConflictException;
import com.web.backend.exception.custom.SystemOverloadException;
import org.springframework.security.access.AccessDeniedException;

@ControllerAdvice
@RequiredArgsConstructor
public class WebSocketErrorHandler {

    private final SimpMessagingTemplate simpMessagingTemplate;

    private static final String QUEUE_ERRORS_STRING = "/queue/errors";

    private static final String ERROR_WS_INVALID_DATA_STRING = "error.ws.invalid_data";
    private static final String ERROR_SYS_BAD_FORMAT_STRING = "error.sys.bad_format";
    private static final String ERROR_SYS_BUSY_STRING = "error.sys.busy";

    public void handleChatError(String username, Object request, String message) {
        simpMessagingTemplate.convertAndSendToUser(
                username,
                QUEUE_ERRORS_STRING,
                ErrorSocketResponse.builder().message(message).request(request).build());
    }

    public void handleChatError(Authentication authentication, String sessionId, Object request, String message) {
        if (authentication != null && authentication.getName() != null) {
            handleChatError(authentication.getName(), request, message);
        } else if (sessionId != null) {
            SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor
                    .create(SimpMessageType.MESSAGE);
            headerAccessor.setSessionId(sessionId);
            headerAccessor.setLeaveMutable(true);
            simpMessagingTemplate.convertAndSendToUser(
                    sessionId,
                    QUEUE_ERRORS_STRING,
                    ErrorSocketResponse.builder().message(message).request(request).build(),
                    headerAccessor.getMessageHeaders());
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

        this.handleChatError(authentication, sessionId, requestData, errorMessage);
    }

    @MessageExceptionHandler(MessageConversionException.class)
    public void handleMessageConversionException(
            MessageConversionException ex,
            Authentication authentication,
            @Header(value = "simpSessionId", required = false) String sessionId) {

        String errorMessage = Translator.tolocale(ERROR_SYS_BAD_FORMAT_STRING);

        this.handleChatError(authentication, sessionId, null, errorMessage);
    }

    @MessageExceptionHandler(AccessForbiddenException.class)
    public void handleAccessForbiddenException(
            AccessForbiddenException ex,
            Authentication authentication,
            @Header(value = "simpSessionId", required = false) String sessionId) {
        this.handleChatError(authentication, sessionId, ex.getRequestData(), ex.getMessage());
    }

    @MessageExceptionHandler(InvalidDataException.class)
    public void handleInvalidDataException(
            InvalidDataException ex,
            Authentication authentication,
            @Header(value = "simpSessionId", required = false) String sessionId) {
        this.handleChatError(authentication, sessionId, ex.getRequestData(), ex.getMessage());
    }

    @MessageExceptionHandler(ResourceNotFoundException.class)
    public void handleResourceNotFoundException(
            ResourceNotFoundException ex,
            Authentication authentication,
            @Header(value = "simpSessionId", required = false) String sessionId) {
        this.handleChatError(authentication, sessionId, ex.getRequestData(), ex.getMessage());
    }

    @MessageExceptionHandler(ResourceConflictException.class)
    public void handleResourceConflictException(
            ResourceConflictException ex,
            Authentication authentication,
            @Header(value = "simpSessionId", required = false) String sessionId) {
        this.handleChatError(authentication, sessionId, ex.getRequestData(), ex.getMessage());
    }

    @MessageExceptionHandler(AccessDeniedException.class)
    public void handleAccessDeniedException(
            AccessDeniedException ex,
            Authentication authentication,
            @Header(value = "simpSessionId", required = false) String sessionId) {

        this.handleChatError(authentication, sessionId, null, ex.getMessage());
    }

    @MessageExceptionHandler(SystemOverloadException.class)
    public void handleSystemOverloadException(
            SystemOverloadException ex,
            Authentication authentication,
            @Header(value = "simpSessionId", required = false) String sessionId) {
        this.handleChatError(authentication, sessionId, ex.getRequestData(), ex.getMessage());
    }

    @MessageExceptionHandler(Exception.class)
    public void handleAllOtherExceptions(
            Exception ex,
            Authentication authentication,
            @Header(value = "simpSessionId", required = false) String sessionId) {

        String errorMessage = Translator.tolocale(ERROR_SYS_BUSY_STRING);

        this.handleChatError(authentication, sessionId, null, errorMessage);
    }
}
