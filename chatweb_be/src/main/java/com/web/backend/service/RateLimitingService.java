package com.web.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RateLimitingService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String RATE_LIMIT_STRING = "rate_limit:";
    private static final String DELIMITE_STRING = ":";

    public boolean allowRequest(String ipAddress, String action, int maxRequests, int timeWindowSeconds) {

        String key = RATE_LIMIT_STRING + action + DELIMITE_STRING + ipAddress;

        Long currentCount = redisTemplate.opsForValue().increment(key);

        if (currentCount != null && currentCount == 1) {
            redisTemplate.expire(key, timeWindowSeconds, TimeUnit.SECONDS);
        }

        return currentCount != null && currentCount <= maxRequests;
    }
}