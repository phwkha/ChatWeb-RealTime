package com.web.backend.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.serializer.RedisSerializer;

@ExtendWith(MockitoExtension.class)
class CuckooFilterServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @InjectMocks
    private CuckooFilterService cuckooFilterService;

    @Test
    void testAdd() {
        cuckooFilterService.add("cf_key", "item_1");

        verify(redisTemplate).execute(
                any(RedisScript.class),
                any(RedisSerializer.class),
                any(RedisSerializer.class),
                any(),
                eq("item_1")
        );
    }

    @Test
    void testExists_ReturnsTrue() {
        when(redisTemplate.execute(
                any(RedisScript.class),
                any(RedisSerializer.class),
                any(RedisSerializer.class),
                any(),
                eq("item_1")
        )).thenReturn(1L);

        boolean exists = cuckooFilterService.exists("cf_key", "item_1");
        assertTrue(exists);
    }

    @Test
    void testExists_ReturnsFalse_WhenZeroOrNull() {
        when(redisTemplate.execute(
                any(RedisScript.class),
                any(RedisSerializer.class),
                any(RedisSerializer.class),
                any(),
                eq("item_1")
        )).thenReturn(0L);

        assertFalse(cuckooFilterService.exists("cf_key", "item_1"));

        when(redisTemplate.execute(
                any(RedisScript.class),
                any(RedisSerializer.class),
                any(RedisSerializer.class),
                any(),
                eq("item_2")
        )).thenReturn(null);

        assertFalse(cuckooFilterService.exists("cf_key", "item_2"));
    }

    @Test
    void testDelete() {
        cuckooFilterService.delete("cf_key", "item_1");

        verify(redisTemplate).execute(
                any(RedisScript.class),
                any(RedisSerializer.class),
                any(RedisSerializer.class),
                any(),
                eq("item_1")
        );
    }
}
