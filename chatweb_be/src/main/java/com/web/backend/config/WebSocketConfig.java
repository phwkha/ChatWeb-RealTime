package com.web.backend.config;

import java.util.Map;
import java.util.Objects;
import java.security.Principal;

import com.web.backend.jwt.JwtHandshakeInterceptor;
import com.web.backend.model.postgres.UserEntity;
import com.web.backend.service.JwtService;
import com.web.backend.service.UserServiceDetail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.messaging.MessageHandler;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import com.web.backend.common.ErrorCode;
import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.controller.response.ErrorSocketResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;
import org.springframework.messaging.support.MessageBuilder;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
@Slf4j(topic = "WEBSOCKET-CONFIG")
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtService jwtService;

    private final UserServiceDetail userServiceDetail;

    private final RedisTemplate<String, Object> redisTemplate;

    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    private final ObjectMapper objectMapper;

    private static final String WS_LOCALE_ATTR = "WS_LOCALE";
    private static final String ONLINE_USERS_KEY = "online_users";

    private static final String ERR_WS_BLACKLISTED = "error.ws.blacklisted";
    private static final String ERR_WS_INVALID_TOKEN_VERSION = "error.ws.invalid_token_version";
    private static final String ERR_WS_MISSING_TOKEN = "error.ws.missing_token";
    private static final String ERR_AUTH_TOKEN_EXPIRED = "error.auth.token_expired";
    private static final String ERR_AUTH_TOKEN_INVALID = "error.auth.token_invalid";

    @Value("${app.cors.allowed-origins:http://localhost:5173}")
    private String allowedOrigins;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.setErrorHandler(createStompErrorHandler());

        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOrigins.split(","))
                .addInterceptors(jwtHandshakeInterceptor)
                .withSockJS();
    }

    private StompSubProtocolErrorHandler createStompErrorHandler() {
        return new StompSubProtocolErrorHandler() {
            @Override
            public Message<byte[]> handleClientMessageProcessingError(Message<byte[]> clientMessage, Throwable ex) {
                Throwable rootCause = extractRootCause(ex);
                ErrorClassification classification = classifyError(rootCause);

                StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.ERROR);
                accessor.setMessage(classification.message());
                accessor.setLeaveMutable(true);

                byte[] payload = serializeErrorPayload(classification);
                return MessageBuilder.createMessage(payload, accessor.getMessageHeaders());
            }
        };
    }

    private record ErrorClassification(int code, ErrorCode errorCode, String message) {}

    private Throwable extractRootCause(Throwable ex) {
        Throwable rootCause = ex;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }
        return rootCause;
    }

    private ErrorClassification classifyError(Throwable rootCause) {
        String msg = rootCause.getMessage();
        if (isTokenExpiredError(rootCause, msg)) {
            return new ErrorClassification(4011, ErrorCode.TOKEN_EXPIRED,
                    Translator.tolocale(ERR_AUTH_TOKEN_EXPIRED));
        }
        if (isTokenInvalidError(rootCause, msg)) {
            return new ErrorClassification(4012, ErrorCode.TOKEN_INVALID,
                    Translator.tolocale(ERR_AUTH_TOKEN_INVALID));
        }

        String fallbackMsg = (msg == null || msg.isBlank())
                ? Translator.tolocale(ERR_AUTH_TOKEN_INVALID)
                : msg;
        return new ErrorClassification(ErrorCode.STOMP_ERROR.getHttpStatus(), ErrorCode.STOMP_ERROR, fallbackMsg);
    }

    private boolean isTokenExpiredError(Throwable rootCause, String message) {
        return rootCause instanceof ExpiredJwtException
                || (message != null && (message.contains("expired") || message.contains("hết hạn")));
    }

    private boolean isTokenInvalidError(Throwable rootCause, String message) {
        return rootCause instanceof JwtException
                || (message != null && (message.contains("token_version")
                        || message.contains("Phiên đăng nhập")
                        || message.contains("phiên đăng nhập")));
    }

    private byte[] serializeErrorPayload(ErrorClassification classification) {
        try {
            ErrorSocketResponse response = ErrorSocketResponse.builder()
                    .code(classification.code())
                    .errorCode(classification.errorCode())
                    .message(classification.message())
                    .request(null)
                    .build();
            return objectMapper.writeValueAsBytes(response);
        } catch (Exception e) {
            return classification.message().getBytes();
        }
    }


    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(createChannelInterceptor());
    }

    private ExecutorChannelInterceptor createChannelInterceptor() {
        return new ExecutorChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor != null) {
                    handleLocaleSetup(accessor);
                    handleAuthentication(accessor);
                    updateOnlineUsers(accessor);
                    if (accessor.isModified()) {
                        return MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
                    }
                }
                return message;
            }

            @Override
            public Message<?> beforeHandle(Message<?> message, MessageChannel channel, MessageHandler handler) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor != null) {
                    Principal user = accessor.getUser();
                    if (user instanceof Authentication auth) {
                        SecurityContext context = SecurityContextHolder.createEmptyContext();
                        context.setAuthentication(auth);
                        SecurityContextHolder.setContext(context);
                    }
                }
                return message;
            }

            @Override
            public void afterMessageHandled(Message<?> message, MessageChannel channel, MessageHandler handler,
                    Exception ex) {
                SecurityContextHolder.clearContext();
            }

            @Override
            public void afterSendCompletion(Message<?> message, MessageChannel channel, boolean sent,
                    Exception ex) {
                LocaleContextHolder.resetLocaleContext();
            }
        };
    }

    private void handleLocaleSetup(StompHeaderAccessor accessor) {
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes == null) {
            return;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String lang = accessor.getFirstNativeHeader(org.springframework.http.HttpHeaders.ACCEPT_LANGUAGE);
            if (lang != null) {
                sessionAttributes.put(WS_LOCALE_ATTR, lang);
                LocaleContextHolder.setLocale(StringUtils.parseLocaleString(lang));
            }
        } else if (sessionAttributes.containsKey(WS_LOCALE_ATTR)) {
            String lang = (String) sessionAttributes.get(WS_LOCALE_ATTR);
            LocaleContextHolder.setLocale(StringUtils.parseLocaleString(lang));
        }
    }

    private void handleAuthentication(StompHeaderAccessor accessor) {
        if (!StompCommand.CONNECT.equals(accessor.getCommand())) {
            return;
        }

        String token = extractTokenFromHeader(accessor);
        if (token == null && accessor.getSessionAttributes() != null) {
            token = (String) accessor.getSessionAttributes().get("jwt_token_cookie");
        }

        if (token == null) {
            throw new MessagingException(Objects.requireNonNull(Translator.tolocale(ERR_WS_MISSING_TOKEN)));
        }

        try {
            validateAndAuthenticateToken(token, accessor);
        } catch (ExpiredJwtException e) {
            log.warn("WebSocket authentication handshake rejected - token expired: {}", e.getMessage());
            throw new MessagingException(
                    Objects.requireNonNull(Translator.tolocale(ERR_AUTH_TOKEN_EXPIRED)), e);
        } catch (JwtException e) {
            log.warn("WebSocket authentication handshake rejected - invalid token: {}", e.getMessage());
            throw new MessagingException(
                    Objects.requireNonNull(Translator.tolocale(ERR_AUTH_TOKEN_INVALID)), e);
        } catch (MessagingException e) {
            throw e;
        } catch (Exception e) {
            log.warn("WebSocket authentication handshake failed: {}", e.getMessage());
            throw new MessagingException(
                    Objects.requireNonNull(Translator.tolocale(ERR_AUTH_TOKEN_INVALID)), e);
        }
    }

    private void validateAndAuthenticateToken(String token, StompHeaderAccessor accessor) {
        String key = "blacklist:" + token;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            log.warn("WebSocket authentication rejected: Token is blacklisted");
            throw new MessagingException(Objects.requireNonNull(Translator.tolocale(ERR_WS_BLACKLISTED)));
        }

        String username = jwtService.extractUsername(token);
        if (username != null) {
            UserDetails userDetails = userServiceDetail.loadUserByUsername(username);

            if (userDetails instanceof UserEntity userEntity) {
                checkTokenVersion(token, userEntity, username);
            }

            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());
            accessor.setUser(auth);
            log.debug("WebSocket handshake authenticated user '{}'", username);
        }
    }

    private void checkTokenVersion(String token, UserEntity userEntity, String username) {
        Integer tokenVersionInJwt = jwtService.extractClaim(token,
                claims -> claims.get("v", Integer.class));
        Integer currentVersion = userEntity.getTokenVersion();
        if (currentVersion == null) {
            currentVersion = 0;
        }

        if (tokenVersionInJwt == null || !tokenVersionInJwt.equals(currentVersion)) {
            log.warn("WebSocket token version mismatch for user '{}'", username);
            throw new MessagingException(
                    Objects.requireNonNull(Translator.tolocale(ERR_WS_INVALID_TOKEN_VERSION)),
                    new JwtException("invalid_token_version"));
        }
    }

    private final Map<String, Long> lastOnlineTimestampMap = new java.util.concurrent.ConcurrentHashMap<>();

    private void updateOnlineUsers(StompHeaderAccessor accessor) {
        Principal user = accessor.getUser();
        if (user != null && user.getName() != null) {
            String username = user.getName();
            if (StompCommand.DISCONNECT.equals(accessor.getCommand())) {
                lastOnlineTimestampMap.remove(username);
                return;
            }
            long now = System.currentTimeMillis();
            Long lastUpdate = lastOnlineTimestampMap.get(username);
            if (lastUpdate == null || now - lastUpdate > 60_000L) {
                lastOnlineTimestampMap.put(username, now);
                try {
                    redisTemplate.opsForZSet().add(ONLINE_USERS_KEY, username, (double) now);
                } catch (Exception e) {
                    log.warn("Failed to update online timestamp in Redis for user '{}'", username, e);
                }
            }
        }
    }

    private String extractTokenFromHeader(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader(org.springframework.http.HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }

}
