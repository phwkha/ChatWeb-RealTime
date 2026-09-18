package com.web.backend.listener;

import com.web.backend.config.ServerIdentity;

import java.security.Principal;
import java.time.Instant;

import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import com.web.backend.service.UserService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j(topic = "WEBSOCKET-LISTENER")
public class WebSocketListener {

    private final UserService userService;

    private final RedisTemplate<String, Object> redisTemplate;

    public static final String ONLINE_USERS_KEY = "online_users";

    public static final String ONLINE_USERS_COUNT_KEY = "online_users_count";

    public static final String OFFLINE_DEBOUNCE_KEY = "presence:offline_queue";

    public static final long DEBOUNCE_DELAY_MS = 5000L;

    private static final String WS_ROUTING_SERVERS_KEY = "ws:routing:servers:";

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal user = headerAccessor.getUser();

        if (user == null || user.getName() == null) {
            return;
        }

        String username = user.getName();

        // Cancel any pending offline debounce in distributed Redis ZSet if user reconnected
        redisTemplate.opsForZSet().remove(OFFLINE_DEBOUNCE_KEY, username);

        Long count = redisTemplate.opsForHash().increment(ONLINE_USERS_COUNT_KEY, username, 1);

        if (count != null && count <= 0) {
            redisTemplate.opsForHash().put(ONLINE_USERS_COUNT_KEY, username, 1L);
            count = 1L;
        }

        redisTemplate.opsForZSet().add(ONLINE_USERS_KEY, username, Instant.now().toEpochMilli());

        if (count != null && count == 1) {
            userService.setUserOnlineStatus(username, true);
            log.info("User '{}' connected (Initial Session)", username);
        } else {
            log.debug("User '{}' opened additional session [totalSessions={}]", username, count);
        }

        redisTemplate.opsForHash().increment(WS_ROUTING_SERVERS_KEY + username, ServerIdentity.SERVER_ID, 1);
        log.debug("Mapped user '{}' session to server node '{}'", username, ServerIdentity.SERVER_ID);
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal user = headerAccessor.getUser();

        if (user == null || user.getName() == null) {
            return;
        }

        String username = user.getName();
        log.debug("WebSocket session disconnected for user '{}'", username);

        Long count = redisTemplate.opsForHash().increment(ONLINE_USERS_COUNT_KEY, username, -1);

        if (count != null && count < 0) {
            redisTemplate.opsForHash().put(ONLINE_USERS_COUNT_KEY, username, 0L);
            count = 0L;
        }

        Long nodeSessions = redisTemplate.opsForHash().increment(WS_ROUTING_SERVERS_KEY + username, ServerIdentity.SERVER_ID, -1);
        if (nodeSessions != null && nodeSessions <= 0) {
            redisTemplate.opsForHash().delete(WS_ROUTING_SERVERS_KEY + username, ServerIdentity.SERVER_ID);
        }

        if (count != null && count <= 0) {
            long offlineDeadline = System.currentTimeMillis() + DEBOUNCE_DELAY_MS;
            redisTemplate.opsForZSet().add(OFFLINE_DEBOUNCE_KEY, username, offlineDeadline);
            log.debug("User session count <= 0. Queued distributed offline debounce for user '{}' until {}", username, offlineDeadline);
        } else {
            log.debug("User '{}' closed one session [remainingSessions={}]", username, count);
        }
    }
}