package com.web.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.web.backend.common.UserStatus;
import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.model.postgres.UserEntity;
import com.web.backend.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceDetailTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserServiceDetail userServiceDetail;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messageSource = mock(ResourceBundleMessageSource.class);
        lenient().when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Mocked Auth Message");
        Translator.setStaticMessageSource(messageSource);
    }

    @Test
    void testLoadUserByUsername_NotFound_ThrowsUsernameNotFoundException() {
        when(userRepository.findWithAuthoritiesByUsername("unknown_user")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> userServiceDetail.loadUserByUsername("unknown_user"));
    }

    @Test
    void testLoadUserByUsername_LockedAccount_ThrowsLockedException() {
        UserEntity user = new UserEntity();
        user.setUsername("locked_user");
        user.setUserStatus(UserStatus.LOCKED);

        when(userRepository.findWithAuthoritiesByUsername("locked_user")).thenReturn(Optional.of(user));

        assertThrows(LockedException.class, () -> userServiceDetail.loadUserByUsername("locked_user"));
    }

    @Test
    void testLoadUserByUsername_UnverifiedAccount_ThrowsDisabledException() {
        UserEntity user = new UserEntity();
        user.setUsername("unverified_user");
        user.setUserStatus(UserStatus.UNVERIFIED);

        when(userRepository.findWithAuthoritiesByUsername("unverified_user")).thenReturn(Optional.of(user));

        assertThrows(DisabledException.class, () -> userServiceDetail.loadUserByUsername("unverified_user"));
    }

    @Test
    void testLoadUserByUsername_InactiveAccount_ThrowsDisabledException() {
        UserEntity user = new UserEntity();
        user.setUsername("inactive_user");
        user.setUserStatus(UserStatus.INACTIVE);

        when(userRepository.findWithAuthoritiesByUsername("inactive_user")).thenReturn(Optional.of(user));

        assertThrows(DisabledException.class, () -> userServiceDetail.loadUserByUsername("inactive_user"));
    }

    @Test
    void testLoadUserByUsername_Success() {
        UserEntity user = new UserEntity();
        user.setUsername("active_user");
        user.setUserStatus(UserStatus.ACTIVE);

        when(userRepository.findWithAuthoritiesByUsername("active_user")).thenReturn(Optional.of(user));

        UserDetails result = userServiceDetail.loadUserByUsername("active_user");
        assertNotNull(result);
        assertEquals("active_user", result.getUsername());
    }
}
