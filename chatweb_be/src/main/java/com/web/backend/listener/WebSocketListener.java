package com.web.backend.listener;

import com.web.backend.config.ServerIdentity;

import java.security.Principal;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private static final String ONLINE_USERS_KEY = "online_users";

    private static final String ONLINE_USERS_COUNT_KEY = "online_users_count";

    private static final String WS_ROUTING_STRING = "ws:routing:";

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal user = headerAccessor.getUser();

        if (user == null || user.getName() == null) {
            return;
        }

        String username = user.getName();
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

        redisTemplate.opsForValue().set(WS_ROUTING_STRING + username, ServerIdentity.SERVER_ID);
        log.debug("Mapped user '{}' to server node '{}'", username, ServerIdentity.SERVER_ID);
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

        if (count != null && count <= 0) {
            log.debug("User session count <= 0. Scheduling offline debounce for user '{}'", username);
            scheduler.schedule(() -> processOfflineDebounce(username), 5, TimeUnit.SECONDS);
        } else {
            log.debug("User '{}' closed one session [remainingSessions={}]", username, count);
        }
    }

    private void processOfflineDebounce(String username) {
        try {
            long currentCount = getCurrentUserCount(username);

            if (currentCount <= 0) {
                redisTemplate.opsForZSet().remove(ONLINE_USERS_KEY, username);
                redisTemplate.opsForHash().delete(ONLINE_USERS_COUNT_KEY, username);
                redisTemplate.delete(WS_ROUTING_STRING + username);
                userService.setUserOnlineStatus(username, false);
                log.info("User '{}' disconnected completely (All sessions closed)", username);
            } else {
                log.debug("User '{}' reconnected during debounce period", username);
            }
        } catch (Exception e) {
            log.error("Error during offline debounce processing for user '{}'", username, e);
        }
    }

    private long getCurrentUserCount(String username) {
        Object currentCountObj = redisTemplate.opsForHash().get(ONLINE_USERS_COUNT_KEY, username);
        if (currentCountObj instanceof Number number) {
            return number.longValue();
        } else if (currentCountObj != null) {
            return Long.parseLong(currentCountObj.toString());
        }
        return 0L;
    }
}