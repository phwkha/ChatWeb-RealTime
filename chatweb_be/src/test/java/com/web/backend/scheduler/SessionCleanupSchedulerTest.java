package com.web.backend.scheduler;

import com.web.backend.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionCleanupSchedulerTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private UserService userService;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private ZSetOperations<String, Object> zSetOperations;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @InjectMocks
    private SessionCleanupScheduler sessionCleanupScheduler;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void testCleanupZombieSessions_LockNotAcquired() {
        when(valueOperations.setIfAbsent(eq("lock:session_cleanup"), anyString(), any(Duration.class)))
                .thenReturn(false);

        sessionCleanupScheduler.cleanupZombieSessions();

        verify(redisTemplate, never()).opsForZSet();
        verify(redisTemplate, never()).execute(any(RedisScript.class), anyList(), any());
    }

    @Test
    void testCleanupZombieSessions_LockAcquired_NoZombies() {
        when(valueOperations.setIfAbsent(eq("lock:session_cleanup"), anyString(), any(Duration.class)))
                .thenReturn(true);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.rangeByScore(eq("online_users"), eq(0.0), anyDouble()))
                .thenReturn(Collections.emptySet());

        sessionCleanupScheduler.cleanupZombieSessions();

        verify(userService, never()).setUserOnlineStatus(anyString(), anyBoolean());
        verify(redisTemplate).execute(any(RedisScript.class), eq(Collections.singletonList("lock:session_cleanup")), anyString());
    }

    @Test
    void testCleanupZombieSessions_LockAcquired_WithZombies() {
        when(valueOperations.setIfAbsent(eq("lock:session_cleanup"), anyString(), any(Duration.class)))
                .thenReturn(true);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);

        Set<Object> zombies = new HashSet<>();
        zombies.add("user1");
        zombies.add("user2");

        when(zSetOperations.rangeByScore(eq("online_users"), eq(0.0), anyDouble()))
                .thenReturn(zombies);

        sessionCleanupScheduler.cleanupZombieSessions();

        verify(zSetOperations).remove("online_users", "user1");
        verify(zSetOperations).remove("online_users", "user2");
        verify(hashOperations).delete("online_users_count", "user1");
        verify(hashOperations).delete("online_users_count", "user2");
        verify(redisTemplate).delete("ws:routing:servers:user1");
        verify(redisTemplate).delete("ws:routing:servers:user2");
        verify(userService).setUserOnlineStatus("user1", false);
        verify(userService).setUserOnlineStatus("user2", false);
        verify(redisTemplate).execute(any(RedisScript.class), eq(Collections.singletonList("lock:session_cleanup")), anyString());
    }

    @Test
    void testCleanupZombieSessions_ExceptionInProcessing_StillReleasesLock() {
        when(valueOperations.setIfAbsent(eq("lock:session_cleanup"), anyString(), any(Duration.class)))
                .thenReturn(true);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.rangeByScore(eq("online_users"), eq(0.0), anyDouble()))
                .thenThrow(new RuntimeException("Redis connection failure"));

        sessionCleanupScheduler.cleanupZombieSessions();

        verify(redisTemplate).execute(any(RedisScript.class), eq(Collections.singletonList("lock:session_cleanup")), anyString());
    }
}
