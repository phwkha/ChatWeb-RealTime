package com.web.backend.service;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j(topic = "CONSUMER-DEDUP-SERVICE")
public class ConsumerDeduplicationService {

    private final StringRedisTemplate stringRedisTemplate;

    private static final String PREFIX = "kafka:dedup:";
    private static final String STATUS_COMPLETED = "COMPLETED";

    public boolean isDuplicate(String consumerName, String eventId, Duration ttl) {
        if (eventId == null || eventId.isBlank()) {
            return false;
        }
        String key = PREFIX + consumerName + ":" + eventId;
        Boolean isFirstTime = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, STATUS_COMPLETED, ttl);

        boolean duplicate = Boolean.FALSE.equals(isFirstTime);
        if (duplicate) {
            log.warn("Duplicate Kafka message detected in consumer '{}' with eventId '{}'", consumerName, eventId);
        }
        return duplicate;
    }

    public void clearOnFailure(String consumerName, String eventId) {
        if (eventId != null && !eventId.isBlank()) {
            String key = PREFIX + consumerName + ":" + eventId;
            stringRedisTemplate.delete(key);
            log.debug("Cleared dedup key '{}' due to processing error", key);
        }
    }
}
