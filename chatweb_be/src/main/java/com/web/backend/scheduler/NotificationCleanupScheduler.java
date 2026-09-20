package com.web.backend.scheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.web.backend.repository.NotificationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j(topic = "NOTIFICATION-CLEANUP")
public class NotificationCleanupScheduler {

    private final RedisTemplate<String, Object> redisTemplate;
    private final NotificationRepository notificationRepository;

    private static final String LOCK_KEY = "lock:notification_cleanup";
    private static final Duration LOCK_TTL = Duration.ofSeconds(60);
    private static final int RETENTION_DAYS = 30;

    private static final String UNLOCK_LUA_SCRIPT = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            else
                return 0
            end
            """;

    private static final RedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(UNLOCK_LUA_SCRIPT, Long.class);

    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void cleanupOldReadNotifications() {
        String lockToken = UUID.randomUUID().toString();
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(LOCK_KEY, lockToken, LOCK_TTL);

        if (!Boolean.TRUE.equals(locked)) {
            log.debug("Notification cleanup skipped: lock acquired by another instance");
            return;
        }

        try {
            Instant cutoffTime = Instant.now().minus(Duration.ofDays(RETENTION_DAYS));
            log.info("Starting notification cleanup for read notifications created before {}", cutoffTime);

            int deletedCount = notificationRepository.deleteReadNotificationsBefore(cutoffTime);
            log.info("Cleaned up {} expired read notifications (older than {} days)", deletedCount, RETENTION_DAYS);
        } catch (Exception e) {
            log.error("Error during notification cleanup execution: {}", e.getMessage(), e);
        } finally {
            releaseLock(LOCK_KEY, lockToken);
        }
    }

    private void releaseLock(String lockKey, String lockToken) {
        try {
            redisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(lockKey), lockToken);
        } catch (Exception e) {
            log.error("Failed to release lock '{}' with token '{}'", lockKey, lockToken, e);
        }
    }
}
