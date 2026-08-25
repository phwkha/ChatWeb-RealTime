package com.web.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;

import com.web.backend.common.UserStatus;
import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.exception.custom.AccessForbiddenException;
import com.web.backend.exception.custom.ResourceNotFoundException;
import com.web.backend.repository.UserRepository;
import com.web.backend.repository.projection.UserRsaKeyProjection;
import com.web.backend.service.impl.KeyServiceImpl;

@ExtendWith(MockitoExtension.class)
class KeyServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private KeyServiceImpl keyService;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messageSource = mock(ResourceBundleMessageSource.class);
        lenient().when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Mocked Error Message");
        Translator.setStaticMessageSource(messageSource);
    }

    @Test
    void testSaveRsaKey_Success() {
        when(userRepository.existsByUsername("testuser")).thenReturn(true);

        keyService.saveRsaKey("testuser", "encrypted_key");
        verify(userRepository).updateEncryptedRsaPrivateKey("testuser", "encrypted_key");
    }

    @Test
    void testSaveRsaKey_NotFound() {
        when(userRepository.existsByUsername("testuser")).thenReturn(false);
        assertThrows(ResourceNotFoundException.class, () -> keyService.saveRsaKey("testuser", "key"));
    }

    @Test
    void testGetRsaKey_Success() {
        when(userRepository.findRsaKeyProjectionByUsername("testuser"))
                .thenReturn(Optional.of(new UserRsaKeyProjection(UserStatus.ACTIVE, "encrypted_key")));

        String key = keyService.getRsaKey("testuser");
        assertEquals("encrypted_key", key);
    }

    @Test
    void testGetRsaKey_NotFound() {
        when(userRepository.findRsaKeyProjectionByUsername("testuser")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> keyService.getRsaKey("testuser"));
    }

    @Test
    void testGetRsaKey_Inactive() {
        when(userRepository.findRsaKeyProjectionByUsername("testuser"))
                .thenReturn(Optional.of(new UserRsaKeyProjection(UserStatus.LOCKED, "encrypted_key")));
        assertThrows(AccessForbiddenException.class, () -> keyService.getRsaKey("testuser"));
    }

    @Test
    void testGetRsaKey_NullKey() {
        when(userRepository.findRsaKeyProjectionByUsername("testuser"))
                .thenReturn(Optional.of(new UserRsaKeyProjection(UserStatus.ACTIVE, null)));

        String key = keyService.getRsaKey("testuser");
        assertNull(key);
    }

    @Test
    void testGetPublicKey_Success() {
        when(userRepository.existsByUsername("testuser")).thenReturn(true);
        when(userRepository.findPublicKeyByUsername("testuser")).thenReturn(Optional.of("pub_key"));

        String key = keyService.getPublicKey("testuser");
        assertEquals("pub_key", key);
    }

    @Test
    void testGetPublicKey_NotFound() {
        when(userRepository.existsByUsername("testuser")).thenReturn(false);
        assertThrows(ResourceNotFoundException.class, () -> keyService.getPublicKey("testuser"));
    }

    @Test
    void testSavePublicKey_Success() {
        when(userRepository.existsByUsername("testuser")).thenReturn(true);

        keyService.savePublicKey("testuser", "pub_key");
        verify(userRepository).updatePublicKey("testuser", "pub_key");
    }

    @Test
    void testSavePublicKey_NotFound() {
        when(userRepository.existsByUsername("testuser")).thenReturn(false);
        assertThrows(ResourceNotFoundException.class, () -> keyService.savePublicKey("testuser", "pub_key"));
    }
}
