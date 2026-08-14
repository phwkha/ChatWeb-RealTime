package com.web.backend.config;

import java.util.Map;
import java.util.Objects;
import java.security.Principal;

import com.web.backend.jwt.JwtHandshakeInterceptor;
import com.web.backend.common.TokenType;
import com.web.backend.model.UserEntity;
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
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.controller.response.ErrorSocketResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;
import org.springframework.messaging.support.MessageBuilder;

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

    private static final String TOPIC_STRING = "/topic";
    private static final String QUEUE_STRING = "/queue";
    private static final String USER_STRING = "/user";
    private static final String APP_STRING = "/app";
    private static final String WS_STRING = "/ws";

    private static final String WS_LOCALE_STRING = "WS_LOCALE";

    private static final String VERSION_STRING = "v";
    private static final String ONLINE_USERS = "online_users";

    private static final String JWT_TOKEN_COOKIE_STRING = "jwt_token_cookie";
    private static final String BLACKLIST_STRING = "blacklist:";
    private static final String AUTHORIZATION_STRING = "Authorization";
    private static final String BEARER_STRING = "Bearer ";
    private static final String ACCEPT_LANGUAGE_STRING = "Accept-Language";

    private static final String ERROR_WS_AUTH_FAILED_STRING = "error.ws.auth_failed";
    private static final String ERROR_WS_BLACKLISTED_STRING = "error.ws.blacklisted";
    private static final String ERROR_WS_INVALID_TOKEN_VERSION_STRING = "error.ws.invalid_token_version";
    private static final String ERROR_WS_MISSING_TOKEN_STRING = "error.ws.missing_token";

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker(TOPIC_STRING, QUEUE_STRING);
        registry.setApplicationDestinationPrefixes(APP_STRING);
        registry.setUserDestinationPrefix(USER_STRING);
    }

    @Value("${app.cors.allowed-origins:http://localhost:5173}")
    private String allowedOrigins;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.setErrorHandler(new StompSubProtocolErrorHandler() {
            @Override
            public Message<byte[]> handleClientMessageProcessingError(Message<byte[]> clientMessage,
                    Throwable ex) {
                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                String errorMessage = cause.getMessage();
                StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.ERROR);
                accessor.setMessage(errorMessage);
                accessor.setLeaveMutable(true);
                byte[] payload;
                try {
                    ErrorSocketResponse response = ErrorSocketResponse.builder().message(errorMessage)
                            .request(null).build();
                    payload = objectMapper.writeValueAsBytes(response);
                } catch (Exception e) {
                    payload = errorMessage != null ? errorMessage.getBytes() : new byte[0];
                }
                return MessageBuilder.createMessage(payload, accessor.getMessageHeaders());
            }
        });

        registry.addEndpoint(WS_STRING)
                .setAllowedOriginPatterns(allowedOrigins.split(","))
                .addInterceptors(jwtHandshakeInterceptor)
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
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
            public void afterSendCompletion(Message<?> message, MessageChannel channel, boolean sent,
                    Exception ex) {
                LocaleContextHolder.resetLocaleContext();
            }
        });
    }

    private void handleLocaleSetup(StompHeaderAccessor accessor) {
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes == null) {
            return;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String lang = accessor.getFirstNativeHeader(ACCEPT_LANGUAGE_STRING);
            if (lang != null) {
                sessionAttributes.put(WS_LOCALE_STRING, lang);
                LocaleContextHolder.setLocale(StringUtils.parseLocaleString(lang));
            }
        } else if (sessionAttributes.containsKey(WS_LOCALE_STRING)) {
            String lang = (String) sessionAttributes.get(WS_LOCALE_STRING);
            LocaleContextHolder.setLocale(StringUtils.parseLocaleString(lang));
        }
    }

    private void handleAuthentication(StompHeaderAccessor accessor) {
        if (!StompCommand.CONNECT.equals(accessor.getCommand())) {
            return;
        }

        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        String token = sessionAttributes != null ? (String) sessionAttributes.get(JWT_TOKEN_COOKIE_STRING) : null;

        if (token == null) {
            token = extractTokenFromHeader(accessor);
        }

        if (token == null) {
            throw new MessagingException(Objects.requireNonNull(Translator.tolocale(ERROR_WS_MISSING_TOKEN_STRING)));
        }

        try {
            validateAndAuthenticateToken(token, accessor);
        } catch (Exception e) {
            log.warn("WebSocket authentication handshake failed: {}", e.getMessage());
            throw new MessagingException(
                    Objects.requireNonNull(Translator.tolocale(ERROR_WS_AUTH_FAILED_STRING, e.getMessage())));
        }
    }

    private void validateAndAuthenticateToken(String token, StompHeaderAccessor accessor) {
        String key = BLACKLIST_STRING + token;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            log.warn("WebSocket authentication rejected: Token is blacklisted");
            throw new MessagingException(Objects.requireNonNull(Translator.tolocale(ERROR_WS_BLACKLISTED_STRING)));
        }

        String username = jwtService.extractUsername(token, TokenType.ACCESS_TOKEN);
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
        Integer tokenVersionInJwt = jwtService.extractClaim(token, TokenType.ACCESS_TOKEN,
                claims -> claims.get(VERSION_STRING, Integer.class));
        Integer currentVersion = userEntity.getTokenVersion();
        if (currentVersion == null) {
            currentVersion = 0;
        }

        if (tokenVersionInJwt == null || !tokenVersionInJwt.equals(currentVersion)) {
            log.warn("WebSocket token version mismatch for user '{}'", username);
            throw new MessagingException(
                    Objects.requireNonNull(Translator.tolocale(ERROR_WS_INVALID_TOKEN_VERSION_STRING)));
        }
    }

    private void updateOnlineUsers(StompHeaderAccessor accessor) {
        Principal user = accessor.getUser();
        if (user != null
                && user.getName() != null
                && !StompCommand.DISCONNECT.equals(accessor.getCommand())) {
            redisTemplate.opsForZSet().add(ONLINE_USERS, user.getName(),
                    System.currentTimeMillis());
        }
    }

    private String extractTokenFromHeader(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader(AUTHORIZATION_STRING);
        if (authHeader != null && authHeader.startsWith(BEARER_STRING)) {
            return authHeader.substring(7);
        }
        return null;
    }

}
