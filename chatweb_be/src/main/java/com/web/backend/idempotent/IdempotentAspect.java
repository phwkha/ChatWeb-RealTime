package com.web.backend.idempotent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.web.backend.controller.response.ApiResponse;
import com.web.backend.exception.custom.DuplicateRequestException;
import com.web.backend.exception.custom.InvalidDataException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j(topic = "IDEMPOTENT-ASPECT")
public class IdempotentAspect {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    private static final String IDEMPOTENT_PREFIX = "idempotent:";
    private static final String DELIMITER = ":";
    private static final String PROCESSING_STATUS = "PROCESSING";
    private static final String COMPLETED_PREFIX = "COMPLETED:";
    private static final String DELIMITER_HASH_STRING = "#";
    private static final String ERROR_SYS_MISSING_IDEMPOTENCY_KEY_STRING = "error.sys.missing_idempotency_key";
    private static final String ANONYMOUS_STRING = "anonymous";

    @Around("@annotation(idempotent)")
    public Object handleIdempotent(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return joinPoint.proceed();
        }
        HttpServletRequest request = attributes.getRequest();

        String idempotencyKey = request.getHeader(idempotent.headerName());
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            if (idempotent.required()) {
                throw new InvalidDataException(ERROR_SYS_MISSING_IDEMPOTENCY_KEY_STRING);
            }
            return joinPoint.proceed();
        }

        String keyPrefix = idempotent.key();
        if (keyPrefix == null || keyPrefix.trim().isEmpty()) {
            keyPrefix = joinPoint.getSignature().getDeclaringType().getSimpleName()
                    + DELIMITER_HASH_STRING
                    + joinPoint.getSignature().getName();
        }
        String username = getAuthenticatedUsername();
        String redisKey = IDEMPOTENT_PREFIX + keyPrefix + DELIMITER
                + (username != null ? username : ANONYMOUS_STRING)
                + DELIMITER + idempotencyKey;

        Duration ttlDuration = Duration.of(idempotent.ttl(), idempotent.unit().toChronoUnit());

        String existingValue = stringRedisTemplate.opsForValue().get(redisKey);

        if (existingValue != null) {
            if (existingValue.equals(PROCESSING_STATUS)) {
                log.warn("Duplicate in-flight request detected for key '{}'", redisKey);
                throw new DuplicateRequestException(idempotent.messageKey());
            }
            if (existingValue.startsWith(COMPLETED_PREFIX)) {
                log.info("Returning cached idempotent response for key '{}'", redisKey);
                String cachedJson = existingValue.substring(COMPLETED_PREFIX.length());
                return deserializeCachedResponse(cachedJson);
            }
        }

        Boolean setResult = stringRedisTemplate.opsForValue()
                .setIfAbsent(redisKey, PROCESSING_STATUS, ttlDuration);

        if (Boolean.FALSE.equals(setResult)) {
            log.warn("Race condition: idempotency key '{}' was just claimed", redisKey);
            throw new DuplicateRequestException(idempotent.messageKey());
        }

        try {
            Object result = joinPoint.proceed();
            String serialized = COMPLETED_PREFIX + serializeResponse(result);
            stringRedisTemplate.opsForValue().set(redisKey, serialized, ttlDuration);
            log.debug("Cached idempotent response for key '{}'", redisKey);
            return result;
        } catch (Exception ex) {
            stringRedisTemplate.delete(redisKey);
            log.debug("Cleared idempotency key '{}' after failure", redisKey);
            throw ex;
        }
    }

    private String getAuthenticatedUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            return auth.getName();
        }
        return null;
    }

    private String serializeResponse(Object result) {
        try {
            if (result instanceof ResponseEntity<?> responseEntity) {
                return objectMapper.writeValueAsString(responseEntity.getBody());
            }
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.warn("Failed to serialize idempotent response: {}", e.getMessage());
            return "{}";
        }
    }

    private Object deserializeCachedResponse(String cachedJson) {
        try {
            ApiResponse<?> cachedApiResponse = objectMapper.readValue(cachedJson, ApiResponse.class);
            return ResponseEntity.ok(cachedApiResponse);
        } catch (Exception e) {
            log.warn("Failed to deserialize cached response: {}", e.getMessage());
            return ResponseEntity.ok(ApiResponse.success(200, "OK", null));
        }
    }
}
