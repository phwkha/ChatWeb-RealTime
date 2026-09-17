package com.web.backend.scheduler;

import com.web.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

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
    private static final Duration LOCK_TTL = Duration.ofSeconds(20);
    private static final String WS_ROUTING_SERVERS_KEY_STRING = "ws:routing:servers:";

    private static final String UNLOCK_LUA_SCRIPT = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            else
                return 0
            end
            """;

    private static final RedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(UNLOCK_LUA_SCRIPT, Long.class);

    @Scheduled(fixedRate = 30 * 1000)
    public void cleanupZombieSessions() {
        String lockToken = UUID.randomUUID().toString();
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(LOCK_KEY, lockToken, LOCK_TTL);

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
            } catch (Exception e) {
                log.error("Error during zombie cleanup execution: {}", e.getMessage(), e);
            } finally {
                try {
                    redisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(LOCK_KEY), lockToken);
                } catch (Exception e) {
                    log.error("Failed to release lock '{}' with token '{}'", LOCK_KEY, lockToken, e);
                }
            }
        }
    }
}
