package com.web.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConsumerDeduplicationServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private ConsumerDeduplicationService dedupService;

    @BeforeEach
    void setUp() {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void isDuplicate_WhenEventIdIsNull_ReturnsFalse() {
        boolean result = dedupService.isDuplicate("email", null, Duration.ofMinutes(10));
        assertFalse(result);
        verifyNoInteractions(valueOperations);
    }

    @Test
    void isDuplicate_WhenEventIdIsBlank_ReturnsFalse() {
        boolean result = dedupService.isDuplicate("email", "   ", Duration.ofMinutes(10));
        assertFalse(result);
        verifyNoInteractions(valueOperations);
    }

    @Test
    void isDuplicate_WhenFirstTime_ReturnsFalse() {
        when(valueOperations.setIfAbsent("kafka:dedup:email:evt-123", "COMPLETED", Duration.ofMinutes(10)))
                .thenReturn(Boolean.TRUE);

        boolean result = dedupService.isDuplicate("email", "evt-123", Duration.ofMinutes(10));
        assertFalse(result);
        verify(valueOperations).setIfAbsent("kafka:dedup:email:evt-123", "COMPLETED", Duration.ofMinutes(10));
    }

    @Test
    void isDuplicate_WhenAlreadyExists_ReturnsTrue() {
        when(valueOperations.setIfAbsent("kafka:dedup:email:evt-123", "COMPLETED", Duration.ofMinutes(10)))
                .thenReturn(Boolean.FALSE);

        boolean result = dedupService.isDuplicate("email", "evt-123", Duration.ofMinutes(10));
        assertTrue(result);
        verify(valueOperations).setIfAbsent("kafka:dedup:email:evt-123", "COMPLETED", Duration.ofMinutes(10));
    }

    @Test
    void clearOnFailure_WhenEventIdIsValid_DeletesKey() {
        dedupService.clearOnFailure("email", "evt-123");
        verify(stringRedisTemplate).delete("kafka:dedup:email:evt-123");
    }

    @Test
    void clearOnFailure_WhenEventIdIsNullOrEmpty_DoesNotDelete() {
        dedupService.clearOnFailure("email", null);
        dedupService.clearOnFailure("email", "  ");
        verify(stringRedisTemplate, never()).delete(anyString());
    }
}
