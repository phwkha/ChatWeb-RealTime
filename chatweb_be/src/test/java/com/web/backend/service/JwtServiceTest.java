package com.web.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.exception.custom.AccessForbiddenException;
import com.web.backend.model.redis.RefreshTokenData;
import com.web.backend.service.impl.JwtServiceImpl;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private JwtServiceImpl jwtService;

    // 32-byte dummy keys, Base64 encoded (required by HS256)
    // "dummy_access_secret_key_which_is_32_bytes_long"
    private final String validAccessKey = "ZHVtbXlfYWNjZXNzX3NlY3JldF9rZXlfd2hpY2hfaXNfMzJfYnl0ZXNfbG9uZw==";

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messageSource = mock(ResourceBundleMessageSource.class);
        lenient().when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Mocked Error Message");
        Translator.setStaticMessageSource(messageSource);

        ReflectionTestUtils.setField(jwtService, "expiryMinutes", 15L);
        ReflectionTestUtils.setField(jwtService, "expiryDay", 7L);
        ReflectionTestUtils.setField(jwtService, "secretKeyAccess", validAccessKey);
    }

    @Test
    void testGenerateAndExtractAccessToken() {
        String token = jwtService.generateAccessToken("testuser", List.of("ROLE_USER"), 1);
        assertNotNull(token);

        String extractedUsername = jwtService.extractUsername(token);
        assertEquals("testuser", extractedUsername);

        Integer version = jwtService.extractClaim(token, claims -> claims.get("v", Integer.class));
        assertEquals(1, version);

        List<?> roles = jwtService.extractClaim(token, claims -> claims.get("role", List.class));
        assertTrue(roles.contains("ROLE_USER"));
    }

    @Test
    void testGenerateRefreshToken() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        String token = jwtService.generateRefreshToken("testadmin", 2);
        assertNotNull(token);

        verify(valueOperations).set(eq("rt:" + token), any(RefreshTokenData.class), eq(7L), eq(TimeUnit.DAYS));
    }

    @Test
    void testValidateRefreshToken_Success() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        RefreshTokenData mockData = RefreshTokenData.builder()
                .username("testuser")
                .tokenVersion(1)
                .createdAt(Instant.now())
                .build();
        when(valueOperations.get("rt:valid-token")).thenReturn(mockData);

        RefreshTokenData result = jwtService.validateRefreshToken("valid-token");
        assertNotNull(result);
        assertEquals("testuser", result.getUsername());
        assertEquals(1, result.getTokenVersion());
    }

    @Test
    void testValidateRefreshToken_NotFound_ThrowsAccessForbiddenException() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("rt:expired-token")).thenReturn(null);

        assertThrows(AccessForbiddenException.class, () -> jwtService.validateRefreshToken("expired-token"));
    }

    @Test
    void testRevokeRefreshToken() {
        jwtService.revokeRefreshToken("test-token");
        verify(redisTemplate).delete("rt:test-token");
    }

    @Test
    void testExtractWithWrongKey_ThrowsSignatureException() {
        // Generate with a DIFFERENT key
        String differentKey = "YW5vdGhlcl9kdW1teV9zZWNyZXRfa2V5XzMyX2J5dGVzX2xvbmc=";
        String invalidToken = Jwts.builder()
                .subject("hacker")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(differentKey)), Jwts.SIG.HS256)
                .compact();

        assertThrows(SignatureException.class, () -> jwtService.extractUsername(invalidToken));
    }

    @Test
    void testGetRemainingTime() {
        String token = jwtService.generateAccessToken("testuser", List.of("ROLE_USER"), 1);

        long remaining = jwtService.getRemainingTime(token);

        // 15 minutes = 15 * 60 * 1000 = 900,000 ms
        assertTrue(remaining > 890000 && remaining <= 900000, "Remaining time should be around 15 minutes");
    }
}
