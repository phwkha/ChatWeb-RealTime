package com.web.backend.exception;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

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

@ExtendWith(MockitoExtension.class)
class WebSocketErrorHandlerTest {

    @Mock
    private WebSocketRoutingService webSocketRoutingService;

    @InjectMocks
    private WebSocketErrorHandler webSocketErrorHandler;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messageSource = mock(ResourceBundleMessageSource.class);
        lenient().when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Mocked Socket Error");
        Translator.setStaticMessageSource(messageSource);
    }

    @Test
    void testHandleChatError_WithUsername() throws Exception {
        webSocketErrorHandler.handleChatError("alice", "sample-request", "System is busy");

        verify(webSocketRoutingService).routeMessage(eq("alice"), eq("/queue/errors"), any(ErrorSocketResponse.class));
    }

    @Test
    void testHandleChatError_WithAuthentication() throws Exception {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("bob");

        webSocketErrorHandler.handleChatError(auth, "sess-1", ErrorCode.ACCESS_DENIED, null, "Denied");

        verify(webSocketRoutingService).routeMessage(eq("bob"), eq("/queue/errors"), any(ErrorSocketResponse.class));
    }

    @Test
    void testHandleChatError_WithSessionIdOnly() {
        webSocketErrorHandler.handleChatError(null, "sess-99", ErrorCode.UNAUTHORIZED, null, "Unauthorized");

        verify(webSocketRoutingService).routeMessageToSession(eq("sess-99"), eq("/queue/errors"), any(ErrorSocketResponse.class));
    }

    @Test
    void testHandleChatError_BothAuthAndSessionNull() throws Exception {
        webSocketErrorHandler.handleChatError(null, null, ErrorCode.UNAUTHORIZED, null, "Unauthorized");

        verify(webSocketRoutingService, never()).routeMessage(any(), any(), any());
        verify(webSocketRoutingService, never()).routeMessageToSession(any(), any(), any());
    }

    @Test
    void testHandleMessageConversionException() throws Exception {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("alice");

        webSocketErrorHandler.handleMessageConversionException(new MessageConversionException("Conversion error"), auth, "sess-1");

        verify(webSocketRoutingService).routeMessage(eq("alice"), eq("/queue/errors"), any(ErrorSocketResponse.class));
    }

    @Test
    void testHandleAccessForbiddenException() throws Exception {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("alice");

        webSocketErrorHandler.handleAccessForbiddenException(new AccessForbiddenException("Forbidden", "data"), auth, "sess-1");

        verify(webSocketRoutingService).routeMessage(eq("alice"), eq("/queue/errors"), any(ErrorSocketResponse.class));
    }

    @Test
    void testHandleInvalidDataException() throws Exception {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("alice");

        webSocketErrorHandler.handleInvalidDataException(new InvalidDataException("Invalid data"), auth, "sess-1");

        verify(webSocketRoutingService).routeMessage(eq("alice"), eq("/queue/errors"), any(ErrorSocketResponse.class));
    }

    @Test
    void testHandleResourceNotFoundException() throws Exception {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("alice");

        webSocketErrorHandler.handleResourceNotFoundException(new ResourceNotFoundException("Not found"), auth, "sess-1");

        verify(webSocketRoutingService).routeMessage(eq("alice"), eq("/queue/errors"), any(ErrorSocketResponse.class));
    }

    @Test
    void testHandleResourceConflictException() throws Exception {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("alice");

        webSocketErrorHandler.handleResourceConflictException(new ResourceConflictException("Conflict"), auth, "sess-1");

        verify(webSocketRoutingService).routeMessage(eq("alice"), eq("/queue/errors"), any(ErrorSocketResponse.class));
    }

    @Test
    void testHandleAccessDeniedException() throws Exception {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("alice");

        webSocketErrorHandler.handleAccessDeniedException(new AccessDeniedException("Access denied"), auth, "sess-1");

        verify(webSocketRoutingService).routeMessage(eq("alice"), eq("/queue/errors"), any(ErrorSocketResponse.class));
    }

    @Test
    void testHandleSystemOverloadException() throws Exception {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("alice");

        webSocketErrorHandler.handleSystemOverloadException(new SystemOverloadException("Overloaded"), auth, "sess-1");

        verify(webSocketRoutingService).routeMessage(eq("alice"), eq("/queue/errors"), any(ErrorSocketResponse.class));
    }

    @Test
    void testHandleTooManyRequestsException() throws Exception {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("alice");

        webSocketErrorHandler.handleTooManyRequestsException(new TooManyRequestsException("error.rate_limit", 10), auth, "sess-1");

        verify(webSocketRoutingService).routeMessage(eq("alice"), eq("/queue/errors"), any(ErrorSocketResponse.class));
    }

    @Test
    void testHandleAuthenticationFailedException() throws Exception {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("alice");

        webSocketErrorHandler.handleAuthenticationFailedException(new AuthenticationFailedException("Auth fail"), auth, "sess-1");

        verify(webSocketRoutingService).routeMessage(eq("alice"), eq("/queue/errors"), any(ErrorSocketResponse.class));
    }

    @Test
    void testHandleConstraintViolationException() throws Exception {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("alice");

        webSocketErrorHandler.handleConstraintViolationException(new ConstraintViolationException("Constraint violated", null), auth, "sess-1");

        verify(webSocketRoutingService).routeMessage(eq("alice"), eq("/queue/errors"), any(ErrorSocketResponse.class));
    }

    @Test
    void testHandleIllegalArgumentException() throws Exception {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("alice");

        webSocketErrorHandler.handleIllegalArgumentException(new IllegalArgumentException("Illegal argument"), auth, "sess-1");

        verify(webSocketRoutingService).routeMessage(eq("alice"), eq("/queue/errors"), any(ErrorSocketResponse.class));
    }

    @Test
    void testHandleIllegalStateException() throws Exception {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("alice");

        webSocketErrorHandler.handleIllegalStateException(new IllegalStateException("Illegal state"), auth, "sess-1");

        verify(webSocketRoutingService).routeMessage(eq("alice"), eq("/queue/errors"), any(ErrorSocketResponse.class));
    }

    @Test
    void testHandleAllOtherExceptions() throws Exception {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("alice");

        webSocketErrorHandler.handleAllOtherExceptions(new RuntimeException("Unknown runtime issue"), auth, "sess-1");

        verify(webSocketRoutingService).routeMessage(eq("alice"), eq("/queue/errors"), any(ErrorSocketResponse.class));
    }
}
