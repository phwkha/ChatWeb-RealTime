package com.web.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.jwt.JwtHandshakeInterceptor;
import com.web.backend.model.postgres.PermissionEntity;
import com.web.backend.model.postgres.RoleEntity;
import com.web.backend.model.postgres.UserEntity;
import com.web.backend.service.JwtService;
import com.web.backend.service.UserServiceDetail;
import java.util.Set;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebSocketConfigTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private UserServiceDetail userServiceDetail;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ZSetOperations<String, Object> zSetOperations;

    @Mock
    private JwtHandshakeInterceptor jwtHandshakeInterceptor;

    private ObjectMapper objectMapper;
    private WebSocketConfig webSocketConfig;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        ResourceBundleMessageSource messageSource = mock(ResourceBundleMessageSource.class);
        lenient().when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Mocked Message");
        Translator.setStaticMessageSource(messageSource);

        webSocketConfig = new WebSocketConfig(
                jwtService,
                userServiceDetail,
                redisTemplate,
                jwtHandshakeInterceptor,
                objectMapper);
        ReflectionTestUtils.setField(webSocketConfig, "allowedOrigins", "http://localhost:3000,http://localhost:5173");
    }

    private StompSubProtocolErrorHandler createStompErrorHandler() {
        return ReflectionTestUtils.invokeMethod(webSocketConfig, "createStompErrorHandler");
    }

    private void handleAuthentication(StompHeaderAccessor accessor) {
        ReflectionTestUtils.invokeMethod(webSocketConfig, "handleAuthentication", accessor);
    }

    private void checkTokenVersion(String token, UserEntity userEntity, String username) {
        ReflectionTestUtils.invokeMethod(webSocketConfig, "checkTokenVersion", token, userEntity, username);
    }

    private void updateOnlineUsers(StompHeaderAccessor accessor) {
        ReflectionTestUtils.invokeMethod(webSocketConfig, "updateOnlineUsers", accessor);
    }

    private void handleLocaleSetup(StompHeaderAccessor accessor) {
        ReflectionTestUtils.invokeMethod(webSocketConfig, "handleLocaleSetup", accessor);
    }

    private ExecutorChannelInterceptor createChannelInterceptor() {
        return ReflectionTestUtils.invokeMethod(webSocketConfig, "createChannelInterceptor");
    }

    // ==========================================
    // 1. Error Handler Tests (registerStompEndpoints & helper methods)
    // ==========================================

    @Test
    void testCreateStompErrorHandler_ExpiredJwtException() {
        StompSubProtocolErrorHandler errorHandler = createStompErrorHandler();
        ExpiredJwtException ex = mock(ExpiredJwtException.class);
        when(ex.getMessage()).thenReturn("JWT expired");

        Message<byte[]> result = errorHandler.handleClientMessageProcessingError(null, ex);

        assertNotNull(result);
        String payload = new String(result.getPayload());
        assertTrue(payload.contains("4011"));
        assertTrue(payload.contains("TOKEN_EXPIRED"));
    }

    @Test
    void testCreateStompErrorHandler_MessageContainsExpired() {
        StompSubProtocolErrorHandler errorHandler = createStompErrorHandler();
        RuntimeException ex = new RuntimeException("The token is expired");

        Message<byte[]> result = errorHandler.handleClientMessageProcessingError(null, ex);

        assertNotNull(result);
        String payload = new String(result.getPayload());
        assertTrue(payload.contains("4011"));
        assertTrue(payload.contains("TOKEN_EXPIRED"));
    }

    @Test
    void testCreateStompErrorHandler_JwtException() {
        StompSubProtocolErrorHandler errorHandler = createStompErrorHandler();
        JwtException ex = new JwtException("Malformed token");

        Message<byte[]> result = errorHandler.handleClientMessageProcessingError(null, ex);

        assertNotNull(result);
        String payload = new String(result.getPayload());
        assertTrue(payload.contains("4012"));
        assertTrue(payload.contains("TOKEN_INVALID"));
    }

    @Test
    void testCreateStompErrorHandler_MessageContainsTokenVersion() {
        StompSubProtocolErrorHandler errorHandler = createStompErrorHandler();
        RuntimeException ex = new RuntimeException("error.ws.invalid_token_version");

        Message<byte[]> result = errorHandler.handleClientMessageProcessingError(null, ex);

        assertNotNull(result);
        String payload = new String(result.getPayload());
        assertTrue(payload.contains("4012"));
        assertTrue(payload.contains("TOKEN_INVALID"));
    }

    @Test
    void testCreateStompErrorHandler_GenericException() {
        StompSubProtocolErrorHandler errorHandler = createStompErrorHandler();
        RuntimeException ex = new RuntimeException("General socket error");

        Message<byte[]> result = errorHandler.handleClientMessageProcessingError(null, ex);

        assertNotNull(result);
        String payload = new String(result.getPayload());
        assertTrue(payload.contains("400"));
        assertTrue(payload.contains("STOMP_ERROR"));
    }

    @Test
    void testCreateStompErrorHandler_NullMessageException() {
        StompSubProtocolErrorHandler errorHandler = createStompErrorHandler();
        RuntimeException ex = new RuntimeException((String) null);

        Message<byte[]> result = errorHandler.handleClientMessageProcessingError(null, ex);

        assertNotNull(result);
        String payload = new String(result.getPayload());
        assertTrue(payload.contains("400"));
        assertTrue(payload.contains("STOMP_ERROR"));
    }

    @Test
    void testCreateStompErrorHandler_NestedRootCause() {
        StompSubProtocolErrorHandler errorHandler = createStompErrorHandler();
        JwtException root = new JwtException("Root invalid token");
        RuntimeException wrapper = new RuntimeException("Wrapper", root);

        Message<byte[]> result = errorHandler.handleClientMessageProcessingError(null, wrapper);

        assertNotNull(result);
        String payload = new String(result.getPayload());
        assertTrue(payload.contains("4012"));
    }

    // ==========================================
    // 2. Authentication Handling Tests
    // ==========================================

    @Test
    void testHandleAuthentication_NonConnectCommand_ShouldDoNothing() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        assertDoesNotThrow(() -> handleAuthentication(accessor));
        assertNull(accessor.getUser());
    }

    @Test
    void testHandleAuthentication_MissingToken_ThrowsMessagingException() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        assertThrows(MessagingException.class, () -> handleAuthentication(accessor));
    }

    @Test
    void testHandleAuthentication_BlacklistedToken_ThrowsMessagingException() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer blacklistedToken");
        when(redisTemplate.hasKey("blacklist:blacklistedToken")).thenReturn(true);

        assertThrows(MessagingException.class, () -> handleAuthentication(accessor));
    }

    @Test
    void testHandleAuthentication_ValidToken_AuthenticatesUser() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer validToken");
        when(redisTemplate.hasKey("blacklist:validToken")).thenReturn(false);
        when(jwtService.extractUsername("validToken")).thenReturn("testuser");

        UserEntity user = new UserEntity();
        user.setUsername("testuser");
        user.setTokenVersion(1);
        when(userServiceDetail.loadUserByUsername("testuser")).thenReturn(user);
        when(jwtService.extractClaim(eq("validToken"), any())).thenReturn(1);

        handleAuthentication(accessor);

        assertNotNull(accessor.getUser());
        assertEquals("testuser", accessor.getUser().getName());
    }

    @Test
    void testHandleAuthentication_SessionAttributeFallbackToken() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        Map<String, Object> sessionAttributes = new HashMap<>();
        sessionAttributes.put("jwt_token_cookie", "cookieToken");
        accessor.setSessionAttributes(sessionAttributes);

        when(redisTemplate.hasKey("blacklist:cookieToken")).thenReturn(false);
        when(jwtService.extractUsername("cookieToken")).thenReturn("cookieUser");
        UserEntity user = new UserEntity();
        user.setUsername("cookieUser");
        user.setTokenVersion(0);
        when(userServiceDetail.loadUserByUsername("cookieUser")).thenReturn(user);
        when(jwtService.extractClaim(eq("cookieToken"), any())).thenReturn(0);

        handleAuthentication(accessor);

        assertNotNull(accessor.getUser());
        assertEquals("cookieUser", accessor.getUser().getName());
    }

    @Test
    void testHandleAuthentication_ExpiredToken_ThrowsMessagingException() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer expiredToken");
        when(redisTemplate.hasKey("blacklist:expiredToken")).thenReturn(false);
        when(jwtService.extractUsername("expiredToken")).thenThrow(mock(ExpiredJwtException.class));

        assertThrows(MessagingException.class, () -> handleAuthentication(accessor));
    }

    @Test
    void testHandleAuthentication_InvalidToken_ThrowsMessagingException() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer invalidToken");
        when(redisTemplate.hasKey("blacklist:invalidToken")).thenReturn(false);
        when(jwtService.extractUsername("invalidToken")).thenThrow(new JwtException("Invalid token"));

        assertThrows(MessagingException.class, () -> handleAuthentication(accessor));
    }

    @Test
    void testCheckTokenVersion_Mismatch_ThrowsMessagingExceptionWithJwtCause() {
        UserEntity user = new UserEntity();
        user.setUsername("user1");
        user.setTokenVersion(2);
        when(jwtService.extractClaim(eq("token"), any())).thenReturn(1);

        MessagingException ex = assertThrows(MessagingException.class,
                () -> checkTokenVersion("token", user, "user1"));
        assertNotNull(ex.getCause());
        assertInstanceOf(JwtException.class, ex.getCause());
    }

    @Test
    void testCheckTokenVersion_NullVersionInUser_DefaultsToZero() {
        UserEntity user = new UserEntity();
        user.setUsername("user1");
        user.setTokenVersion(null);
        when(jwtService.extractClaim(eq("token"), any())).thenReturn(0);

        assertDoesNotThrow(() -> checkTokenVersion("token", user, "user1"));
    }

    // ==========================================
    // 3. Online Users Tracking Tests
    // ==========================================

    @Test
    void testUpdateOnlineUsers_NoUser_ShouldDoNothing() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        assertDoesNotThrow(() -> updateOnlineUsers(accessor));
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void testUpdateOnlineUsers_Disconnect_ShouldRemoveUser() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn("activeUser");
        accessor.setUser(principal);

        assertDoesNotThrow(() -> updateOnlineUsers(accessor));
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void testUpdateOnlineUsers_ActiveUser_UpdatesRedisZSet() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn("userZSetTest");
        accessor.setUser(principal);

        updateOnlineUsers(accessor);

        verify(zSetOperations).add(eq("online_users"), eq("userZSetTest"), anyDouble());
    }

    @Test
    void testUpdateOnlineUsers_RedisException_DoesNotPropagate() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        doThrow(new RuntimeException("Redis error")).when(zSetOperations).add(anyString(), any(), anyDouble());

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn("userExceptionTest");
        accessor.setUser(principal);

        assertDoesNotThrow(() -> updateOnlineUsers(accessor));
    }

    // ==========================================
    // 4. Broker and Registration Configuration Tests
    // ==========================================

    @Test
    void testConfigureMessageBroker() {
        MessageBrokerRegistry registry = mock(MessageBrokerRegistry.class);
        webSocketConfig.configureMessageBroker(registry);

        verify(registry).enableSimpleBroker("/topic", "/queue");
        verify(registry).setApplicationDestinationPrefixes("/app");
        verify(registry).setUserDestinationPrefix("/user");
    }

    @Test
    void testRegisterStompEndpoints() {
        StompEndpointRegistry registry = mock(StompEndpointRegistry.class);
        StompWebSocketEndpointRegistration registration = mock(StompWebSocketEndpointRegistration.class);
        when(registry.addEndpoint("/ws")).thenReturn(registration);
        when(registration.setAllowedOriginPatterns(any(String[].class))).thenReturn(registration);
        when(registration.addInterceptors(any())).thenReturn(registration);

        webSocketConfig.registerStompEndpoints(registry);

        verify(registry).setErrorHandler(any(StompSubProtocolErrorHandler.class));
        verify(registry).addEndpoint("/ws");
        verify(registration).withSockJS();
    }

    // ==========================================
    // 5. Inbound Channel Interceptor Tests
    // ==========================================

    @Test
    void testConfigureClientInboundChannel() {
        ChannelRegistration registration = mock(ChannelRegistration.class);
        webSocketConfig.configureClientInboundChannel(registration);
        verify(registration).interceptors(any(ExecutorChannelInterceptor.class));
    }

    @Test
    void testHandleLocaleSetup_NullAttributes_DoesNothing() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionAttributes(null);
        assertDoesNotThrow(() -> handleLocaleSetup(accessor));
    }

    @Test
    void testHandleLocaleSetup_ConnectWithLanguageHeader() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        Map<String, Object> sessionAttributes = new HashMap<>();
        accessor.setSessionAttributes(sessionAttributes);
        accessor.setNativeHeader("Accept-Language", "vi");

        handleLocaleSetup(accessor);

        assertEquals("vi", sessionAttributes.get("WS_LOCALE"));
    }

    @Test
    void testHandleLocaleSetup_NonConnectWithStoredLanguage() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        Map<String, Object> sessionAttributes = new HashMap<>();
        sessionAttributes.put("WS_LOCALE", "en");
        accessor.setSessionAttributes(sessionAttributes);

        handleLocaleSetup(accessor);

        assertNotNull(sessionAttributes.get("WS_LOCALE"));
    }

    @Test
    void testCreateChannelInterceptor_PreSend_NullAccessor() {
        ExecutorChannelInterceptor interceptor = createChannelInterceptor();
        Message<String> message = MessageBuilder.withPayload("test").build();

        Message<?> result = interceptor.preSend(message, mock(MessageChannel.class));
        assertSame(message, result);
    }

    @Test
    void testCreateChannelInterceptor_PreSend_ValidAccessor() {
        ExecutorChannelInterceptor interceptor = createChannelInterceptor();
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setUser(mock(Principal.class));
        Message<String> message = MessageBuilder.createMessage("test", accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, mock(MessageChannel.class));
        assertNotNull(result);
    }

    @Test
    void testCreateChannelInterceptor_BeforeHandle_WithAuthentication() {
        ExecutorChannelInterceptor interceptor = createChannelInterceptor();
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        Authentication auth = mock(Authentication.class);
        accessor.setUser(auth);
        Message<String> message = MessageBuilder.createMessage("test", accessor.getMessageHeaders());

        Message<?> result = interceptor.beforeHandle(message, mock(MessageChannel.class), mock(MessageHandler.class));
        assertSame(message, result);
        assertEquals(auth, SecurityContextHolder.getContext().getAuthentication());
        SecurityContextHolder.clearContext();
    }

    @Test
    void testCreateChannelInterceptor_AfterMessageHandled_ClearsContext() {
        ExecutorChannelInterceptor interceptor = createChannelInterceptor();
        SecurityContextHolder.getContext().setAuthentication(mock(Authentication.class));

        interceptor.afterMessageHandled(mock(Message.class), mock(MessageChannel.class), mock(MessageHandler.class),
                null);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void testCreateChannelInterceptor_AfterSendCompletion_ResetsLocale() {
        ExecutorChannelInterceptor interceptor = createChannelInterceptor();
        assertDoesNotThrow(
                () -> interceptor.afterSendCompletion(mock(Message.class), mock(MessageChannel.class), true, null));
    }

    @Test
    void testBeforeHandle_OnSendCommand_RefreshesAuthoritiesFromUserServiceDetail() {
        ExecutorChannelInterceptor interceptor = createChannelInterceptor();
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("testuser");
        accessor.setUser(auth);
        Message<String> message = MessageBuilder.createMessage("test", accessor.getMessageHeaders());

        UserEntity user = new UserEntity();
        user.setUsername("testuser");
        RoleEntity role = new RoleEntity();
        PermissionEntity perm = new PermissionEntity();
        perm.setName("USER_SEND_WORLD_CHAT");
        role.setPermissions(Set.of(perm));
        user.setRole(role);
        when(userServiceDetail.loadUserByUsername("testuser")).thenReturn(user);

        Message<?> result = interceptor.beforeHandle(message, mock(MessageChannel.class), mock(MessageHandler.class));
        assertSame(message, result);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("testuser", SecurityContextHolder.getContext().getAuthentication().getName());
        assertEquals(1, SecurityContextHolder.getContext().getAuthentication().getAuthorities().size());
        assertTrue(SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> "USER_SEND_WORLD_CHAT".equals(a.getAuthority())));
        SecurityContextHolder.clearContext();
    }

    @Test
    void testBeforeHandle_OnSendCommand_WhenUserLocked_ThrowsLockedException() {
        ExecutorChannelInterceptor interceptor = createChannelInterceptor();
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("lockeduser");
        accessor.setUser(auth);
        Message<String> message = MessageBuilder.createMessage("test", accessor.getMessageHeaders());

        when(userServiceDetail.loadUserByUsername("lockeduser")).thenThrow(new LockedException("Account locked"));

        assertThrows(LockedException.class,
                () -> interceptor.beforeHandle(message, mock(MessageChannel.class), mock(MessageHandler.class)));
        SecurityContextHolder.clearContext();
    }

    @Test
    void testBeforeHandle_OnSendCommand_WhenTokenVersionChanged_ThrowsMessagingException() {
        ExecutorChannelInterceptor interceptor = createChannelInterceptor();
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("testuser");
        accessor.setUser(auth);
        accessor.setSessionAttributes(new HashMap<>(Map.of("WS_TOKEN_VERSION", 1)));
        Message<String> message = MessageBuilder.createMessage("test", accessor.getMessageHeaders());

        UserEntity user = new UserEntity();
        user.setUsername("testuser");
        user.setTokenVersion(2);
        when(userServiceDetail.loadUserByUsername("testuser")).thenReturn(user);

        assertThrows(MessagingException.class,
                () -> interceptor.beforeHandle(message, mock(MessageChannel.class), mock(MessageHandler.class)));
        SecurityContextHolder.clearContext();
    }

    @Test
    void testBeforeHandle_NullAccessor_ReturnsMessage() {
        ExecutorChannelInterceptor interceptor = createChannelInterceptor();
        Message<String> message = new GenericMessage<>("payload-without-stomp-headers");
        Message<?> result = interceptor.beforeHandle(message, mock(MessageChannel.class), mock(MessageHandler.class));
        assertSame(message, result);
    }

    @Test
    void testBeforeHandle_NullUser_DoesNotSetAuthentication() {
        ExecutorChannelInterceptor interceptor = createChannelInterceptor();
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        Message<String> message = MessageBuilder.createMessage("test", accessor.getMessageHeaders());
        SecurityContextHolder.clearContext();

        Message<?> result = interceptor.beforeHandle(message, mock(MessageChannel.class), mock(MessageHandler.class));
        assertSame(message, result);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void testBeforeHandle_NonAuthenticationPrincipal_DoesNotSetAuthentication() {
        ExecutorChannelInterceptor interceptor = createChannelInterceptor();
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        Principal nonAuthPrincipal = () -> "regularUser";
        accessor.setUser(nonAuthPrincipal);
        Message<String> message = MessageBuilder.createMessage("test", accessor.getMessageHeaders());
        SecurityContextHolder.clearContext();

        Message<?> result = interceptor.beforeHandle(message, mock(MessageChannel.class), mock(MessageHandler.class));
        assertSame(message, result);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void testBeforeHandle_NonSendCommandWithAuth_DoesNotRefresh() {
        ExecutorChannelInterceptor interceptor = createChannelInterceptor();
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        Authentication auth = mock(Authentication.class);
        accessor.setUser(auth);
        Message<String> message = MessageBuilder.createMessage("test", accessor.getMessageHeaders());

        Message<?> result = interceptor.beforeHandle(message, mock(MessageChannel.class), mock(MessageHandler.class));
        assertSame(message, result);
        assertSame(auth, SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(userServiceDetail);
        SecurityContextHolder.clearContext();
    }

    @Test
    void testBeforeHandle_OnSendCommand_NullAuthName_DoesNotRefresh() {
        ExecutorChannelInterceptor interceptor = createChannelInterceptor();
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn(null);
        accessor.setUser(auth);
        Message<String> message = MessageBuilder.createMessage("test", accessor.getMessageHeaders());

        Message<?> result = interceptor.beforeHandle(message, mock(MessageChannel.class), mock(MessageHandler.class));
        assertSame(message, result);
        assertSame(auth, SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(userServiceDetail);
        SecurityContextHolder.clearContext();
    }

    @Test
    void testBeforeHandle_OnSendCommand_UserNotFound_ReturnsCurrentAuth() {
        ExecutorChannelInterceptor interceptor = createChannelInterceptor();
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("unknownUser");
        accessor.setUser(auth);
        Message<String> message = MessageBuilder.createMessage("test", accessor.getMessageHeaders());

        when(userServiceDetail.loadUserByUsername("unknownUser")).thenReturn(null);

        Message<?> result = interceptor.beforeHandle(message, mock(MessageChannel.class), mock(MessageHandler.class));
        assertSame(message, result);
        assertSame(auth, SecurityContextHolder.getContext().getAuthentication());
        SecurityContextHolder.clearContext();
    }

    @Test
    void testBeforeHandle_OnSendCommand_NonUserEntity_RefreshesWithoutTokenVersionCheck() {
        ExecutorChannelInterceptor interceptor = createChannelInterceptor();
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("otherUser");
        accessor.setUser(auth);
        accessor.setSessionAttributes(new HashMap<>(Map.of("WS_TOKEN_VERSION", 1)));
        Message<String> message = MessageBuilder.createMessage("test", accessor.getMessageHeaders());

        org.springframework.security.core.userdetails.UserDetails userDetails =
                new org.springframework.security.core.userdetails.User(
                        "otherUser", "password", java.util.Collections.emptyList());
        when(userServiceDetail.loadUserByUsername("otherUser")).thenReturn(userDetails);

        Message<?> result = interceptor.beforeHandle(message, mock(MessageChannel.class), mock(MessageHandler.class));
        assertSame(message, result);
        Authentication updatedAuth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(updatedAuth);
        assertEquals("otherUser", updatedAuth.getName());
        SecurityContextHolder.clearContext();
    }

    @Test
    void testBeforeHandle_OnSendCommand_NullSessionAttributes_RefreshesWithoutTokenVersionCheck() {
        ExecutorChannelInterceptor interceptor = createChannelInterceptor();
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("testuser");
        accessor.setUser(auth);
        accessor.setSessionAttributes(null);
        Message<String> message = MessageBuilder.createMessage("test", accessor.getMessageHeaders());

        UserEntity user = new UserEntity();
        user.setUsername("testuser");
        user.setTokenVersion(1);
        when(userServiceDetail.loadUserByUsername("testuser")).thenReturn(user);

        Message<?> result = interceptor.beforeHandle(message, mock(MessageChannel.class), mock(MessageHandler.class));
        assertSame(message, result);
        Authentication updatedAuth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(updatedAuth);
        assertEquals("testuser", updatedAuth.getName());
        SecurityContextHolder.clearContext();
    }

    @Test
    void testBeforeHandle_OnSendCommand_NullTokenVersionInUser_DefaultsToZero() {
        ExecutorChannelInterceptor interceptor = createChannelInterceptor();
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("testuser");
        accessor.setUser(auth);
        accessor.setSessionAttributes(new HashMap<>(Map.of("WS_TOKEN_VERSION", 0)));
        Message<String> message = MessageBuilder.createMessage("test", accessor.getMessageHeaders());

        UserEntity user = new UserEntity();
        user.setUsername("testuser");
        user.setTokenVersion(null);
        when(userServiceDetail.loadUserByUsername("testuser")).thenReturn(user);

        Message<?> result = interceptor.beforeHandle(message, mock(MessageChannel.class), mock(MessageHandler.class));
        assertSame(message, result);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        SecurityContextHolder.clearContext();
    }

    @Test
    void testBeforeHandle_OnSendCommand_NullSessionTokenVersion_DoesNotThrow() {
        ExecutorChannelInterceptor interceptor = createChannelInterceptor();
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("testuser");
        accessor.setUser(auth);
        accessor.setSessionAttributes(new HashMap<>());
        Message<String> message = MessageBuilder.createMessage("test", accessor.getMessageHeaders());

        UserEntity user = new UserEntity();
        user.setUsername("testuser");
        user.setTokenVersion(5);
        when(userServiceDetail.loadUserByUsername("testuser")).thenReturn(user);

        Message<?> result = interceptor.beforeHandle(message, mock(MessageChannel.class), mock(MessageHandler.class));
        assertSame(message, result);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        SecurityContextHolder.clearContext();
    }

    @Test
    void testBeforeHandle_OnSendCommand_MatchingTokenVersion_RefreshesSuccessfully() {
        ExecutorChannelInterceptor interceptor = createChannelInterceptor();
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("testuser");
        accessor.setUser(auth);
        accessor.setSessionAttributes(new HashMap<>(Map.of("WS_TOKEN_VERSION", 3)));
        Message<String> message = MessageBuilder.createMessage("test", accessor.getMessageHeaders());

        UserEntity user = new UserEntity();
        user.setUsername("testuser");
        user.setTokenVersion(3);
        when(userServiceDetail.loadUserByUsername("testuser")).thenReturn(user);

        Message<?> result = interceptor.beforeHandle(message, mock(MessageChannel.class), mock(MessageHandler.class));
        assertSame(message, result);
        Authentication updatedAuth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(updatedAuth);
        assertEquals("testuser", updatedAuth.getName());
        SecurityContextHolder.clearContext();
    }

    @Test
    void testBeforeHandle_OnSendCommand_MutableAccessor_SetsUserOnAccessor() {
        ExecutorChannelInterceptor interceptor = createChannelInterceptor();
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setLeaveMutable(true);
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("testuser");
        accessor.setUser(auth);
        accessor.setSessionAttributes(new HashMap<>(Map.of("WS_TOKEN_VERSION", 1)));

        Message<?> message = MessageBuilder.createMessage("test", accessor.getMessageHeaders());

        UserEntity user = new UserEntity();
        user.setUsername("testuser");
        user.setTokenVersion(1);
        when(userServiceDetail.loadUserByUsername("testuser")).thenReturn(user);

        assertTrue(accessor.isMutable());
        interceptor.beforeHandle(message, mock(MessageChannel.class), mock(MessageHandler.class));

        assertNotSame(auth, accessor.getUser());
        assertEquals("testuser", accessor.getUser().getName());
        SecurityContextHolder.clearContext();
    }

    @Test
    void testCreateChannelInterceptor_PreSend_AccessorModified() {
        ExecutorChannelInterceptor interceptor = createChannelInterceptor();
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        accessor.setNativeHeader("Authorization", "Bearer valid.token");
        accessor.setSessionAttributes(new HashMap<>());

        when(jwtService.extractUsername("valid.token")).thenReturn("testuser");
        UserEntity user = new UserEntity();
        user.setUsername("testuser");
        user.setTokenVersion(1);
        when(userServiceDetail.loadUserByUsername("testuser")).thenReturn(user);
        when(jwtService.extractClaim(eq("valid.token"), any())).thenReturn(1);

        Message<?> message = MessageBuilder.createMessage("connect payload", accessor.getMessageHeaders());
        Message<?> result = interceptor.preSend(message, mock(MessageChannel.class));

        assertNotNull(result);
        assertEquals("connect payload", result.getPayload());
    }

    @Test
    void testCreateStompErrorHandler_SerializePayloadException_Fallback() throws Exception {
        ObjectMapper throwingMapper = mock(ObjectMapper.class);
        when(throwingMapper.writeValueAsBytes(any())).thenThrow(new RuntimeException("JSON error"));
        ReflectionTestUtils.setField(webSocketConfig, "objectMapper", throwingMapper);

        try {
            StompSubProtocolErrorHandler errorHandler = createStompErrorHandler();
            Message<byte[]> result = errorHandler.handleClientMessageProcessingError(null, new RuntimeException("Generic Error"));

            assertNotNull(result);
            assertNotNull(result.getPayload());
            assertEquals("Generic Error", new String(result.getPayload()));
        } finally {
            ReflectionTestUtils.setField(webSocketConfig, "objectMapper", objectMapper);
        }
    }

    @Test
    void testHandleAuthentication_GeneralException_WrapsInMessagingException() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer valid.token");

        when(jwtService.extractUsername("valid.token")).thenThrow(new RuntimeException("Database connection failure"));

        MessagingException ex = assertThrows(MessagingException.class,
                () -> handleAuthentication(accessor));
        assertNotNull(ex.getCause());
        assertEquals("Database connection failure", ex.getCause().getMessage());
    }

    @Test
    void testCheckTokenVersion_ExecutesClaimsResolverLambda() {
        String token = "sample.token";
        UserEntity user = new UserEntity();
        user.setUsername("testuser");
        user.setTokenVersion(1);

        Claims claims = mock(Claims.class);
        when(claims.get("v", Integer.class)).thenReturn(1);
        when(jwtService.extractClaim(eq(token), any())).thenAnswer(inv -> {
            java.util.function.Function<Claims, Integer> resolver = inv.getArgument(1);
            return resolver.apply(claims);
        });

        assertDoesNotThrow(() -> checkTokenVersion(token, user, "testuser"));
    }
}
