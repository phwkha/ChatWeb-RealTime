package com.web.backend.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

class CustomCacheErrorHandlerTest {

    private CustomCacheErrorHandler errorHandler;
    private Cache cache;

    @BeforeEach
    void setUp() {
        errorHandler = new CustomCacheErrorHandler();
        cache = mock(Cache.class);
        when(cache.getName()).thenReturn("user_details");
    }

    @Test
    void testHandleCacheGetError_ShouldEvictKey() {
        RuntimeException ex = new RuntimeException("Serialization error");
        assertDoesNotThrow(() -> errorHandler.handleCacheGetError(ex, cache, "testUser"));
        verify(cache, times(1)).evict("testUser");
    }

    @Test
    void testHandleCacheGetError_EvictThrowsException_ShouldNotPropagate() {
        RuntimeException ex = new RuntimeException("Deserialization failed");
        doThrow(new RuntimeException("Redis connection error")).when(cache).evict("testUser");

        assertDoesNotThrow(() -> errorHandler.handleCacheGetError(ex, cache, "testUser"));
        verify(cache, times(1)).evict("testUser");
    }

    @Test
    void testHandleCacheGetError_NullCacheOrKey_ShouldNotThrow() {
        RuntimeException ex = new RuntimeException("Deserialization failed");
        assertDoesNotThrow(() -> errorHandler.handleCacheGetError(ex, null, "testUser"));
        assertDoesNotThrow(() -> errorHandler.handleCacheGetError(ex, cache, null));
        assertDoesNotThrow(() -> errorHandler.handleCacheGetError(ex, null, null));
    }

    @Test
    void testHandleCachePutError_ShouldNotThrow() {
        RuntimeException ex = new RuntimeException("Redis put error");
        assertDoesNotThrow(() -> errorHandler.handleCachePutError(ex, cache, "testUser", "someValue"));
        assertDoesNotThrow(() -> errorHandler.handleCachePutError(ex, null, "testUser", "someValue"));
    }

    @Test
    void testHandleCacheEvictError_ShouldNotThrow() {
        RuntimeException ex = new RuntimeException("Redis evict error");
        assertDoesNotThrow(() -> errorHandler.handleCacheEvictError(ex, cache, "testUser"));
        assertDoesNotThrow(() -> errorHandler.handleCacheEvictError(ex, null, "testUser"));
    }

    @Test
    void testHandleCacheClearError_ShouldNotThrow() {
        RuntimeException ex = new RuntimeException("Redis clear error");
        assertDoesNotThrow(() -> errorHandler.handleCacheClearError(ex, cache));
        assertDoesNotThrow(() -> errorHandler.handleCacheClearError(ex, null));
    }
}
