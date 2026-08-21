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
    private static final String DELIMITER_DASH_STRING = "-";

    private static final String LUA_SLIDING_WINDOW_SCRIPT = """
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local limit = tonumber(ARGV[3])
            local member = ARGV[4]
            local clearBefore = now - (window * 1000)

            redis.call('ZREMRANGEBYSCORE', key, '-inf', clearBefore)
            local currentRequests = redis.call('ZCARD', key)

            if currentRequests < limit then
                redis.call('ZADD', key, now, member)
                redis.call('EXPIRE', key, window + 2)
                return 1
            else
                return 0
            end
            """;

    private final RedisScript<Long> slidingWindowRedisScript = new DefaultRedisScript<>(LUA_SLIDING_WINDOW_SCRIPT,
            Long.class);

    public boolean isAllowed(String targetKey, int maxRequests, long windowSeconds) {
        String fullKey = RATE_LIMIT_PREFIX + targetKey;
        long now = System.currentTimeMillis();
        String member = now + DELIMITER_DASH_STRING + UUID.randomUUID().toString().substring(0, 8);

        try {
            Long result = stringRedisTemplate.execute(
                    slidingWindowRedisScript,
                    Collections.singletonList(fullKey),
                    String.valueOf(now),
                    String.valueOf(windowSeconds),
                    String.valueOf(maxRequests),
                    member);

            return result != null && result == 1L;
        } catch (Exception ex) {
            log.error("Error executing Redis rate limit Lua script for key {}: {}", fullKey, ex.getMessage(), ex);
            return true;
        }
    }

    public boolean allowRequest(String ipAddress, String action, int maxRequests, int timeWindowSeconds) {
        String targetKey = action + DELIMITER + ipAddress;
        return isAllowed(targetKey, maxRequests, timeWindowSeconds);
    }
}