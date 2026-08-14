package com.web.backend.scheduler;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j(topic = "USER-HEARTBEAT")
public class UserHeartbeatScheduler {

    private final SimpUserRegistry simpUserRegistry;
    private final RedisTemplate<String, Object> redisTemplate;
    private static final String ONLINE_USERS_KEY = "online_users";

    @Scheduled(fixedRate = 60 * 1000)
    public void updateLocalUsersHeartbeat() {
        try {
            long currentTime = System.currentTimeMillis();
            for (SimpUser user : simpUserRegistry.getUsers()) {
                String username = user.getName();
                if (username != null) {
                    redisTemplate.opsForZSet().add(ONLINE_USERS_KEY, username, currentTime);
                }
            }
        } catch (Exception e) {
            log.error("Failed to refresh user heartbeats in Redis", e);
        }
    }
}
