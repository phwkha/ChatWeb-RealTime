package com.web.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

@Slf4j(topic = "CUSTOM-CACHE-ERROR-HANDLER")
public class CustomCacheErrorHandler implements CacheErrorHandler {

    private static final String UNKNOW_STRING = "unknown";

    @Override
    public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
        String cacheName = cache != null ? cache.getName() : UNKNOW_STRING;
        log.warn(
                "Redis cache GET failed for cache='{}', key='{}'. Evicting corrupted key and falling back to DB. Error: {}",
                cacheName, key, exception.getMessage());
        if (cache != null && key != null) {
            try {
                cache.evict(key);
            } catch (Exception e) {
                log.error("Failed to evict corrupted cache key='{}' from cache='{}'", key, cacheName, e);
            }
        }
    }

    @Override
    public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
        String cacheName = cache != null ? cache.getName() : UNKNOW_STRING;
        log.warn("Redis cache PUT failed for cache='{}', key='{}'. Error: {}",
                cacheName, key, exception.getMessage());
    }

    @Override
    public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
        String cacheName = cache != null ? cache.getName() : UNKNOW_STRING;
        log.warn("Redis cache EVICT failed for cache='{}', key='{}'. Error: {}",
                cacheName, key, exception.getMessage());
    }

    @Override
    public void handleCacheClearError(RuntimeException exception, Cache cache) {
        String cacheName = cache != null ? cache.getName() : UNKNOW_STRING;
        log.warn("Redis cache CLEAR failed for cache='{}'. Error: {}",
                cacheName, exception.getMessage());
    }
}
