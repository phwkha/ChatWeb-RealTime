package com.web.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.test.util.ReflectionTestUtils;

import com.web.backend.common.TokenType;
import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.service.impl.JwtServiceImpl;

import io.jsonwebtoken.security.SignatureException;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    @InjectMocks
    private JwtServiceImpl jwtService;

    // 32-byte dummy keys, Base64 encoded (required by HS256)
    // "dummy_access_secret_key_which_is_32_bytes_long"
    private final String validAccessKey = "ZHVtbXlfYWNjZXNzX3NlY3JldF9rZXlfd2hpY2hfaXNfMzJfYnl0ZXNfbG9uZw==";
    // "dummy_refresh_secret_key_which_is_32_bytes_long"
    private final String validRefreshKey = "ZHVtbXlfcmVmcmVzaF9zZWNyZXRfa2V5X3doaWNoX2lzXzMyX2J5dGVzX2xvbmc=";

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messageSource = mock(ResourceBundleMessageSource.class);
        lenient().when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Mocked Error Message");
        Translator.setStaticMessageSource(messageSource);

        ReflectionTestUtils.setField(jwtService, "expiryMinutes", 15L);
        ReflectionTestUtils.setField(jwtService, "expiryDay", 7L);
        ReflectionTestUtils.setField(jwtService, "secretKeyAccess", validAccessKey);
        ReflectionTestUtils.setField(jwtService, "secretKeyRefresh", validRefreshKey);
    }

    @Test
    void testGenerateAndExtractAccessToken() {
        String token = jwtService.generateAccessToken("testuser", List.of("ROLE_USER"), 1);
        assertNotNull(token);

        String extractedUsername = jwtService.extractUsername(token, TokenType.ACCESS_TOKEN);
        assertEquals("testuser", extractedUsername);

        Integer version = jwtService.extractClaim(token, TokenType.ACCESS_TOKEN,
                claims -> claims.get("v", Integer.class));
        assertEquals(1, version);

        List<?> roles = jwtService.extractClaim(token, TokenType.ACCESS_TOKEN,
                claims -> claims.get("role", List.class));
        assertTrue(roles.contains("ROLE_USER"));
    }

    @Test
    void testGenerateAndExtractRefreshToken() {
        String token = jwtService.generateRefreshToken("testadmin", List.of("ROLE_ADMIN"), 2);
        assertNotNull(token);

        String extractedUsername = jwtService.extractUsername(token, TokenType.REFRESH_TOKEN);
        assertEquals("testadmin", extractedUsername);

        Integer version = jwtService.extractClaim(token, TokenType.REFRESH_TOKEN,
                claims -> claims.get("v", Integer.class));
        assertEquals(2, version);
    }

    @Test
    void testExtractWithWrongKey_ThrowsSignatureException() {
        // Generate with ACCESS key
        String token = jwtService.generateAccessToken("testuser", List.of("ROLE_USER"), 1);

        // Try to parse with REFRESH key -> Should throw exception
        assertThrows(SignatureException.class, () -> jwtService.extractUsername(token, TokenType.REFRESH_TOKEN));
    }

    @Test
    void testGetRemainingTime() {
        String token = jwtService.generateAccessToken("testuser", List.of("ROLE_USER"), 1);

        long remaining = jwtService.getRemainingTime(token, TokenType.ACCESS_TOKEN);

        // 15 minutes = 15 * 60 * 1000 = 900,000 ms
        // Remaining time should be very close to 900,000 ms
        assertTrue(remaining > 890000 && remaining <= 900000, "Remaining time should be around 15 minutes");
    }
}
