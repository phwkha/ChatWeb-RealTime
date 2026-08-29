package com.web.backend.idempotent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.web.backend.controller.response.ApiResponse;
import com.web.backend.exception.custom.DuplicateRequestException;
import com.web.backend.exception.custom.InvalidDataException;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotentAspectTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private ProceedingJoinPoint joinPoint;
    @Mock
    private Idempotent idempotent;
    @Mock
    private HttpServletRequest request;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private Signature signature;

    @InjectMocks
    private IdempotentAspect idempotentAspect;

    @BeforeEach
    void setUp() {
        ServletRequestAttributes attributes = mock(ServletRequestAttributes.class);
        lenient().when(attributes.getRequest()).thenReturn(request);
        RequestContextHolder.setRequestAttributes(attributes);
        SecurityContextHolder.clearContext();
    }

    @Test
    void testFirstRequest_ShouldProceedAndCacheResponse() throws Throwable {
        when(idempotent.headerName()).thenReturn("X-Idempotency-Key");
        when(request.getHeader("X-Idempotency-Key")).thenReturn("test-uuid-123");
        when(idempotent.key()).thenReturn("test_endpoint");
        when(idempotent.ttl()).thenReturn(300);
        when(idempotent.unit()).thenReturn(java.util.concurrent.TimeUnit.SECONDS);

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        String expectedRedisKey = "idempotent:test_endpoint:anonymous:test-uuid-123";
        when(valueOperations.get(expectedRedisKey)).thenReturn(null);
        when(valueOperations.setIfAbsent(eq(expectedRedisKey), eq("PROCESSING"), any(Duration.class)))
                .thenReturn(true);

        ResponseEntity<String> mockResponse = ResponseEntity.ok("Success");
        when(joinPoint.proceed()).thenReturn(mockResponse);
        when(objectMapper.writeValueAsString(any())).thenReturn("\"Success\"");

        Object result = idempotentAspect.handleIdempotent(joinPoint, idempotent);

        assertEquals(mockResponse, result);
        verify(valueOperations).set(eq(expectedRedisKey), eq("COMPLETED:\"Success\""), any(Duration.class));
    }

    @Test
    void testMissingHeader_WhenRequired_ShouldThrowException() {
        when(idempotent.headerName()).thenReturn("X-Idempotency-Key");
        when(request.getHeader("X-Idempotency-Key")).thenReturn(null);
        when(idempotent.required()).thenReturn(true);

        assertThrows(InvalidDataException.class, () -> {
            idempotentAspect.handleIdempotent(joinPoint, idempotent);
        });
    }

    @Test
    void testMissingHeader_WhenOptional_ShouldProceed() throws Throwable {
        when(idempotent.headerName()).thenReturn("X-Idempotency-Key");
        when(request.getHeader("X-Idempotency-Key")).thenReturn(null);
        when(idempotent.required()).thenReturn(false);
        when(joinPoint.proceed()).thenReturn("OK");

        Object result = idempotentAspect.handleIdempotent(joinPoint, idempotent);

        assertEquals("OK", result);
        verifyNoInteractions(stringRedisTemplate);
    }

    @Test
    void testDuplicateRequest_WithProcessingKey_ShouldThrowConflict() {
        when(idempotent.headerName()).thenReturn("X-Idempotency-Key");
        when(request.getHeader("X-Idempotency-Key")).thenReturn("test-uuid-123");
        when(idempotent.key()).thenReturn("test_endpoint");
        when(idempotent.ttl()).thenReturn(300);
        when(idempotent.unit()).thenReturn(java.util.concurrent.TimeUnit.SECONDS);

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        String expectedRedisKey = "idempotent:test_endpoint:anonymous:test-uuid-123";
        when(valueOperations.get(expectedRedisKey)).thenReturn("PROCESSING");

        assertThrows(DuplicateRequestException.class, () -> {
            idempotentAspect.handleIdempotent(joinPoint, idempotent);
        });
    }

    @Test
    void testFailedRequest_ShouldClearRedisKey() throws Throwable {
        when(idempotent.headerName()).thenReturn("X-Idempotency-Key");
        when(request.getHeader("X-Idempotency-Key")).thenReturn("test-uuid-123");
        when(idempotent.key()).thenReturn("test_endpoint");
        when(idempotent.ttl()).thenReturn(300);
        when(idempotent.unit()).thenReturn(java.util.concurrent.TimeUnit.SECONDS);

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        String expectedRedisKey = "idempotent:test_endpoint:anonymous:test-uuid-123";
        when(valueOperations.get(expectedRedisKey)).thenReturn(null);
        when(valueOperations.setIfAbsent(eq(expectedRedisKey), eq("PROCESSING"), any(Duration.class)))
                .thenReturn(true);

        when(joinPoint.proceed()).thenThrow(new RuntimeException("Business Logic Error"));

        assertThrows(RuntimeException.class, () -> {
            idempotentAspect.handleIdempotent(joinPoint, idempotent);
        });

        verify(stringRedisTemplate).delete(expectedRedisKey);
    }
}
