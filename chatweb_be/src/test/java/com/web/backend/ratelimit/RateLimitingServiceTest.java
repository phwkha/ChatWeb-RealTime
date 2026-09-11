package com.web.backend.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import com.web.backend.service.RateLimitingService;

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
                ArgumentMatchers.<RedisScript<Long>>any(),
                anyList(),
                any(), any(), any(), any()
        )).thenReturn(1L);

        boolean allowed = rateLimitingService.isAllowed("login:127.0.0.1", 5, 60);
        assertThat(allowed).isTrue();
    }

    @Test
    void testIsAllowed_LimitExceeded() {
        when(stringRedisTemplate.execute(
                ArgumentMatchers.<RedisScript<Long>>any(),
                anyList(),
                any(), any(), any(), any()
        )).thenReturn(0L);

        boolean allowed = rateLimitingService.isAllowed("login:127.0.0.1", 5, 60);
        assertThat(allowed).isFalse();
    }

    @Test
    void testIsAllowed_RedisException_FailOpen() {
        when(stringRedisTemplate.execute(
                ArgumentMatchers.<RedisScript<Long>>any(),
                anyList(),
                any(), any(), any(), any()
        )).thenThrow(new RuntimeException("Redis connection error"));

        boolean allowed = rateLimitingService.isAllowed("login:127.0.0.1", 5, 60);
        assertThat(allowed).isTrue();
    }

    @Test
    void testAllowRequest_LegacyMethod() {
        when(stringRedisTemplate.execute(
                ArgumentMatchers.<RedisScript<Long>>any(),
                anyList(),
                any(), any(), any(), any()
        )).thenReturn(1L);

        boolean allowed = rateLimitingService.allowRequest("127.0.0.1", "login", 5, 60);
        assertThat(allowed).isTrue();
    }
}
