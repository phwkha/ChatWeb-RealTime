package com.web.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.GenericToStringSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j(topic = "CUCKOO-FILTER")
public class CuckooFilterService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String LUA_CF_ADD_SCRIPT = "return redis.call('CF.ADD', KEYS[1], ARGV[1])";
    private static final String LUA_CF_EXISTS_SCRIPT = "return redis.call('CF.EXISTS', KEYS[1], ARGV[1])";
    private static final String LUA_CF_DEL_SCRIPT = "return redis.call('CF.DEL', KEYS[1], ARGV[1])";

    /**
     * Thêm item vào Cuckoo Filter dùng Lua script
     */
    public void add(String key, String item) {
        redisTemplate.execute(
                new DefaultRedisScript<>(LUA_CF_ADD_SCRIPT, Long.class),
                RedisSerializer.string(),
                new GenericToStringSerializer<>(Long.class),
                Objects.requireNonNull(Collections.singletonList(key)),
                item);
    }

    /**
     * Kiểm tra tồn tại trong Cuckoo Filter
     */
    public boolean exists(String key, String item) {
        Long result = redisTemplate.execute(
                new DefaultRedisScript<>(LUA_CF_EXISTS_SCRIPT, Long.class),
                RedisSerializer.string(),
                new GenericToStringSerializer<>(Long.class),
                Objects.requireNonNull(Collections.singletonList(key)),
                item);
        return result != null && result == 1L;
    }

    /**
     * Xóa item khỏi Cuckoo Filter
     */
    public void delete(String key, String item) {
        redisTemplate.execute(
                new DefaultRedisScript<>(LUA_CF_DEL_SCRIPT, Long.class),
                RedisSerializer.string(),
                new GenericToStringSerializer<>(Long.class),
                Objects.requireNonNull(Collections.singletonList(key)),
                item);
        log.debug("Deleted item '{}' from Cuckoo filter '{}'", item, key);
    }
}