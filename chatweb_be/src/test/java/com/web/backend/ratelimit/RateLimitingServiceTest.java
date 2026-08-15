package com.web.backend.ratelimit;

import com.web.backend.service.RateLimitingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitingServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    private RateLimitingService rateLimitingService;

    @BeforeEach
    void setUp() {
        rateLimitingService = new RateLimitingService(stringRedisTemplate);
    }

    @Test
    void testIsAllowed_Success() {
        when(stringRedisTemplate.execute(
                any(RedisScript.class),
                anyList(),
                any(), any(), any(), any()
        )).thenReturn(1L);

        boolean allowed = rateLimitingService.isAllowed("login:127.0.0.1", 5, 60);
        assertTrue(allowed);
    }

    @Test
    void testIsAllowed_LimitExceeded() {
        when(stringRedisTemplate.execute(
                any(RedisScript.class),
                anyList(),
                any(), any(), any(), any()
        )).thenReturn(0L);

        boolean allowed = rateLimitingService.isAllowed("login:127.0.0.1", 5, 60);
        assertFalse(allowed);
    }

    @Test
    void testIsAllowed_RedisException_FailOpen() {
        when(stringRedisTemplate.execute(
                any(RedisScript.class),
                anyList(),
                any(), any(), any(), any()
        )).thenThrow(new RuntimeException("Redis connection error"));

        boolean allowed = rateLimitingService.isAllowed("login:127.0.0.1", 5, 60);
        assertTrue(allowed);
    }

    @Test
    void testAllowRequest_LegacyMethod() {
        when(stringRedisTemplate.execute(
                any(RedisScript.class),
                anyList(),
                any(), any(), any(), any()
        )).thenReturn(1L);

        boolean allowed = rateLimitingService.allowRequest("127.0.0.1", "login", 5, 60);
        assertTrue(allowed);
    }
}
