package com.web.backend.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import com.web.backend.repository.NotificationRepository;

@ExtendWith(MockitoExtension.class)
class NotificationCleanupSchedulerTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private NotificationCleanupScheduler notificationCleanupScheduler;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void testCleanupOldReadNotifications_LockNotAcquired() {
        when(valueOperations.setIfAbsent(eq("lock:notification_cleanup"), anyString(), any(Duration.class)))
                .thenReturn(false);

        notificationCleanupScheduler.cleanupOldReadNotifications();

        verify(notificationRepository, never()).deleteReadNotificationsBefore(any());
        verify(redisTemplate, never()).execute(ArgumentMatchers.<RedisScript<Long>>any(), anyList(), any());
    }

    @Test
    void testCleanupOldReadNotifications_Success() {
        when(valueOperations.setIfAbsent(eq("lock:notification_cleanup"), anyString(), any(Duration.class)))
                .thenReturn(true);
        when(notificationRepository.deleteReadNotificationsBefore(any(Instant.class))).thenReturn(15);

        notificationCleanupScheduler.cleanupOldReadNotifications();

        verify(notificationRepository).deleteReadNotificationsBefore(any(Instant.class));
        verify(redisTemplate).execute(ArgumentMatchers.<RedisScript<Long>>any(), anyList(), any());
    }

    @Test
    void testCleanupOldReadNotifications_ExceptionHandled_LockAlwaysReleased() {
        when(valueOperations.setIfAbsent(eq("lock:notification_cleanup"), anyString(), any(Duration.class)))
                .thenReturn(true);
        when(notificationRepository.deleteReadNotificationsBefore(any(Instant.class)))
                .thenThrow(new RuntimeException("DB connection error"));

        notificationCleanupScheduler.cleanupOldReadNotifications();

        verify(redisTemplate).execute(ArgumentMatchers.<RedisScript<Long>>any(), anyList(), any());
    }
}
