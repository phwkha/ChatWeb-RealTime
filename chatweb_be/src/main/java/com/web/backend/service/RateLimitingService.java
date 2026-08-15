package com.web.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j(topic = "RATE-LIMITING-SERVICE")
public class RateLimitingService {

    private final StringRedisTemplate stringRedisTemplate;

    private static final String RATE_LIMIT_PREFIX = "rate_limit:";
    private static final String DELIMITER = ":";

    // Lua script for atomic sliding window rate limiting using Redis ZSET
    private static final String LUA_SLIDING_WINDOW_SCRIPT =
            "local key = KEYS[1]\n" +
            "local now = tonumber(ARGV[1])\n" +
            "local window = tonumber(ARGV[2])\n" +
            "local limit = tonumber(ARGV[3])\n" +
            "local member = ARGV[4]\n" +
            "local clearBefore = now - (window * 1000)\n" +
            "\n" +
            "redis.call('ZREMRANGEBYSCORE', key, '-inf', clearBefore)\n" +
            "local currentRequests = redis.call('ZCARD', key)\n" +
            "\n" +
            "if currentRequests < limit then\n" +
            "    redis.call('ZADD', key, now, member)\n" +
            "    redis.call('EXPIRE', key, window + 2)\n" +
            "    return 1\n" +
            "else\n" +
            "    return 0\n" +
            "end";

    private final RedisScript<Long> slidingWindowRedisScript = new DefaultRedisScript<>(LUA_SLIDING_WINDOW_SCRIPT, Long.class);

    /**
     * Checks if the request is allowed using the Sliding Window Log algorithm.
     *
     * @param targetKey Identifier for the rate limit subject (e.g., "login:192.168.1.1")
     * @param maxRequests Max requests allowed in the window
     * @param windowSeconds Time window in seconds
     * @return true if request is allowed, false if limit exceeded
     */
    public boolean isAllowed(String targetKey, int maxRequests, long windowSeconds) {
        String fullKey = RATE_LIMIT_PREFIX + targetKey;
        long now = System.currentTimeMillis();
        String member = now + "-" + UUID.randomUUID().toString().substring(0, 8);

        try {
            Long result = stringRedisTemplate.execute(
                    slidingWindowRedisScript,
                    Collections.singletonList(fullKey),
                    String.valueOf(now),
                    String.valueOf(windowSeconds),
                    String.valueOf(maxRequests),
                    member
            );

            return result != null && result == 1L;
        } catch (Exception ex) {
            log.error("Error executing Redis rate limit Lua script for key {}: {}", fullKey, ex.getMessage(), ex);
            // In case of Redis outage, fail open or fail close depending on policy.
            // Failing open (return true) to avoid breaking application when Redis is degraded.
            return true;
        }
    }

    /**
     * Legacy method backward compatibility.
     */
    public boolean allowRequest(String ipAddress, String action, int maxRequests, int timeWindowSeconds) {
        String targetKey = action + DELIMITER + ipAddress;
        return isAllowed(targetKey, maxRequests, timeWindowSeconds);
    }
}