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
import com.web.backend.model.UserEntity;
import com.web.backend.repository.UserRepository;
import com.web.backend.service.impl.KeyServiceImpl;

@ExtendWith(MockitoExtension.class)
class KeyServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private KeyServiceImpl keyService;

    private UserEntity activeUser;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messageSource = mock(ResourceBundleMessageSource.class);
        lenient().when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Mocked Error Message");
        Translator.setStaticMessageSource(messageSource);

        activeUser = new UserEntity();
        activeUser.setUsername("testuser");
        activeUser.setUserStatus(UserStatus.ACTIVE);
    }

    @Test
    void testSaveRsaKey_Success() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(activeUser));
        when(userRepository.save(activeUser)).thenReturn(activeUser);

        keyService.saveRsaKey("testuser", "encrypted_key");
        assertEquals("encrypted_key", activeUser.getEncryptedRsaPrivateKey());
    }

    @Test
    void testSaveRsaKey_NotFound() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> keyService.saveRsaKey("testuser", "key"));
    }

    @Test
    void testGetRsaKey_Success() {
        activeUser.setEncryptedRsaPrivateKey("encrypted_key");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(activeUser));

        String key = keyService.getRsaKey("testuser");
        assertEquals("encrypted_key", key);
    }

    @Test
    void testGetRsaKey_NotFound() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> keyService.getRsaKey("testuser"));
    }

    @Test
    void testGetRsaKey_Inactive() {
        activeUser.setUserStatus(UserStatus.LOCKED);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(activeUser));
        assertThrows(AccessForbiddenException.class, () -> keyService.getRsaKey("testuser"));
    }

    @Test
    void testGetRsaKey_NullKey() {
        activeUser.setEncryptedRsaPrivateKey(null);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(activeUser));

        String key = keyService.getRsaKey("testuser");
        assertNull(key);
    }

    @Test
    void testGetPublicKey_Success() {
        activeUser.setPublicKey("pub_key");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(activeUser));

        String key = keyService.getPublicKey("testuser");
        assertEquals("pub_key", key);
    }

    @Test
    void testGetPublicKey_NotFound() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> keyService.getPublicKey("testuser"));
    }

    @Test
    void testSavePublicKey_Success() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(activeUser));
        when(userRepository.save(activeUser)).thenReturn(activeUser);

        keyService.savePublicKey("testuser", "pub_key");
        assertEquals("pub_key", activeUser.getPublicKey());
    }

    @Test
    void testSavePublicKey_NotFound() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> keyService.savePublicKey("testuser", "pub_key"));
    }
}
