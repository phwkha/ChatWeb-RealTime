package com.web.backend.scheduler;

import com.web.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.time.Duration;

@Component
@RequiredArgsConstructor
@Slf4j(topic = "SESSION-CLEANUP")
public class SessionCleanupScheduler {

    private final RedisTemplate<String, Object> redisTemplate;
    private final UserService userService;
    private static final String ONLINE_USERS_KEY = "online_users";
    private static final String ONLINE_USERS_COUNT_KEY = "online_users_count";
    private static final long TIMEOUT_MS = 3L * 60 * 1000;

    private static final String LOCK_KEY = "lock:session_cleanup";
    private static final String LOCKED_VALUE_STRING = "locked";
    private static final String WS_ROUTING_SERVERS_KEY_STRING = "ws:routing:servers:";

    @Scheduled(fixedRate = 30 * 1000)
    public void cleanupZombieSessions() {
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(LOCK_KEY, LOCKED_VALUE_STRING, Duration.ofSeconds(20));

        if (Boolean.TRUE.equals(locked)) {
            try {
                long timeoutLimit = System.currentTimeMillis() - TIMEOUT_MS;

                Set<Object> zombieUsers = redisTemplate.opsForZSet().rangeByScore(ONLINE_USERS_KEY, 0, timeoutLimit);

                if (zombieUsers != null && !zombieUsers.isEmpty()) {
                    log.info("Detected {} zombie sessions. Initiating cleanup...", zombieUsers.size());
                    for (Object userObj : zombieUsers) {
                        String username = (String) userObj;
                        redisTemplate.opsForZSet().remove(ONLINE_USERS_KEY, username);
                        redisTemplate.opsForHash().delete(ONLINE_USERS_COUNT_KEY, username);
                        redisTemplate.delete(WS_ROUTING_SERVERS_KEY_STRING + username);
                        userService.setUserOnlineStatus(username, false);
                        log.debug("Cleaned up zombie session for user '{}'", username);
                    }
                }
            } finally {
                redisTemplate.delete(LOCK_KEY);
            }
        }
    }
}
