package com.web.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.context.support.ResourceBundleMessageSource;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import com.web.backend.common.TokenType;
import com.web.backend.common.UserStatus;
import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.controller.request.CreateUserRequest;
import com.web.backend.controller.request.LoginRequest;
import com.web.backend.controller.request.VerifyOtpRequest;
import com.web.backend.model.redis.RegisterData;
import com.web.backend.exception.custom.InvalidOtpException;
import com.web.backend.controller.response.LoginResponse;
import com.web.backend.controller.response.TokenResponse;
import com.web.backend.controller.response.UserResponse;
import com.web.backend.exception.custom.AccessForbiddenException;
import com.web.backend.exception.custom.AuthenticationFailedException;
import com.web.backend.exception.custom.InvalidDataException;
import com.web.backend.exception.custom.ResourceConflictException;
import com.web.backend.exception.custom.ResourceNotFoundException;
import com.web.backend.kafka.producer.EmailProducer;
import com.web.backend.mapper.UserMapper;
import com.web.backend.model.postgres.RoleEntity;
import com.web.backend.model.postgres.UserEntity;
import com.web.backend.repository.RoleRepository;
import com.web.backend.repository.UserRepository;
import com.web.backend.service.impl.AuthenticationServiceImpl;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserMapper userMapper;

    @Mock
    private JwtService jwtService;

    @Mock
    private EmailProducer emailKafkaProducer;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache userCache;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private CuckooFilterService cuckooFilterService;

    @InjectMocks
    private AuthenticationServiceImpl authenticationService;

    private UserEntity mockUser;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messageSource = mock(ResourceBundleMessageSource.class);
        lenient().when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Mocked Error Message");
        Translator.setStaticMessageSource(messageSource);

        mockUser = new UserEntity();
        mockUser.setId(1L);
        mockUser.setUsername("testuser");
        mockUser.setEmail("test@example.com");
        mockUser.setPassword("encodedPassword");
        mockUser.setTokenVersion(1);
    }

    // ==========================================
    // TESTS FOR LOGIN()
    // ==========================================

    @Test
    void testLogin_Success() {
        // Arrange
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("testuser");
        loginRequest.setPassword("password123");

        Authentication authentication = mock(Authentication.class);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(authentication.getAuthorities()).thenReturn(Collections.emptyList());
        when(authentication.getPrincipal()).thenReturn(mockUser);

        when(jwtService.generateAccessToken(anyString(), any(), anyInt())).thenReturn("mockAccessToken");
        when(jwtService.generateRefreshToken(anyString(), any(), anyInt())).thenReturn("mockRefreshToken");

        UserResponse mockUserResponse = UserResponse.builder().username("testuser").build();
        when(userMapper.toUserResponse(mockUser)).thenReturn(mockUserResponse);

        // Act
        LoginResponse response = authenticationService.login(loginRequest);

        // Assert
        assertNotNull(response);
        assertEquals("mockAccessToken", response.getAccessToken());
        assertEquals("mockRefreshToken", response.getRefreshToken());
        assertEquals("testuser", response.getUserResponse().getUsername());

        verify(authenticationManager, times(1)).authenticate(any());
        verify(jwtService, times(1)).generateAccessToken(eq("testuser"), any(), eq(1));
    }

    @Test
    void testLogin_WrongPassword_ThrowsAuthenticationFailedException() {
        // Arrange
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("testuser");
        loginRequest.setPassword("wrongpassword");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        // Act & Assert
        assertThrows(AuthenticationFailedException.class, () -> authenticationService.login(loginRequest));
        verify(jwtService, never()).generateAccessToken(anyString(), any(), anyInt());
    }

    @Test
    void testLogin_AccountLocked_ThrowsAccessForbiddenException() {
        // Arrange
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("testuser");
        loginRequest.setPassword("password123");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new LockedException("Account locked"));

        // Act & Assert
        assertThrows(AccessForbiddenException.class, () -> authenticationService.login(loginRequest));
    }

    // ==========================================
    // TESTS FOR CREATEUSER()
    // ==========================================

    @Test
    void testCreateUser_Success() {
        // Arrange
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername("newuser");
        request.setEmail("newuser@example.com");
        request.setPassword("password123");

        when(cuckooFilterService.exists(anyString(), eq("newuser@example.com"))).thenReturn(false);
        when(cuckooFilterService.exists(anyString(), eq("newuser"))).thenReturn(false);

        RoleEntity role = new RoleEntity();
        role.setId(1L);
        role.setName("USER");
        when(roleRepository.findByName("USER")).thenReturn(Optional.of(role));

        when(passwordEncoder.encode("password123")).thenReturn("encoded123");

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doNothing().when(valueOperations).set(anyString(), any(), anyLong(), any());
        doNothing().when(emailKafkaProducer).sendOtpEmailTask(anyString(), anyString(), anyString());

        // Act
        UserResponse response = authenticationService.createUser(request);

        // Assert
        assertNotNull(response);
        assertEquals("newuser", response.getUsername());
        assertEquals("newuser@example.com", response.getEmail());
        assertEquals(UserStatus.UNVERIFIED, response.getUserStatus());

        verify(emailKafkaProducer, times(1)).sendOtpEmailTask(eq("newuser@example.com"), eq("newuser"), anyString());
    }

    @Test
    void testCreateUser_EmailAlreadyExists_ThrowsResourceConflictException() {
        // Arrange
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername("newuser");
        request.setEmail("existing@example.com");

        when(cuckooFilterService.exists(anyString(), eq("existing@example.com"))).thenReturn(true);
        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        // Act & Assert
        assertThrows(ResourceConflictException.class, () -> authenticationService.createUser(request));

        // Đảm bảo không có email OTP nào được gửi
        verify(emailKafkaProducer, never()).sendOtpEmailTask(anyString(), anyString(), anyString());
    }

    // ==========================================
    // TESTS FOR REFRESHTOKEN()
    // ==========================================

    @Test
    void testRefreshToken_Success() {
        // Arrange
        String oldRefreshToken = "oldRefresh";
        when(jwtService.extractUsername(oldRefreshToken, TokenType.REFRESH_TOKEN)).thenReturn("testuser");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(mockUser));
        when(redisTemplate.hasKey("blacklist:" + oldRefreshToken)).thenReturn(false);

        // Mock extractClaim for token version
        when(jwtService.extractClaim(eq(oldRefreshToken), eq(TokenType.REFRESH_TOKEN), any())).thenReturn(1);

        mockUser.setUserStatus(UserStatus.ACTIVE);
        when(jwtService.generateAccessToken(eq("testuser"), any(), eq(1))).thenReturn("newAccess");
        when(jwtService.generateRefreshToken(eq("testuser"), any(), eq(1))).thenReturn("newRefresh");
        when(jwtService.getRemainingTime(oldRefreshToken, TokenType.REFRESH_TOKEN)).thenReturn(1000L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // Act
        TokenResponse response = authenticationService.refreshToken(oldRefreshToken);

        // Assert
        assertNotNull(response);
        assertEquals("newAccess", response.getAccessToken());
        assertEquals("newRefresh", response.getRefreshToken());
        verify(valueOperations).set(eq("blacklist:oldRefresh"), eq("rotated"), eq(1000L), any());
    }

    @Test
    void testRefreshToken_MissingToken_ThrowsInvalidDataException() {
        assertThrows(InvalidDataException.class, () -> authenticationService.refreshToken(null));
        assertThrows(InvalidDataException.class, () -> authenticationService.refreshToken(""));
    }

    @Test
    void testRefreshToken_Blacklisted_ThrowsAccessForbiddenException() {
        // Arrange
        String badToken = "badToken";
        when(jwtService.extractUsername(badToken, TokenType.REFRESH_TOKEN)).thenReturn("testuser");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(mockUser));
        when(redisTemplate.hasKey("blacklist:" + badToken)).thenReturn(true);
        when(cacheManager.getCache("user_details")).thenReturn(userCache);

        // Act & Assert
        assertThrows(AccessForbiddenException.class, () -> authenticationService.refreshToken(badToken));

        // Verify account token version incremented and cache evicted
        assertEquals(2, mockUser.getTokenVersion());
        verify(userRepository).save(mockUser);
        verify(userCache).evict("testuser");
    }

    @Test
    void testRefreshToken_InvalidVersion_ThrowsAccessForbiddenException() {
        // Arrange
        String oldRefreshToken = "oldRefresh";
        when(jwtService.extractUsername(oldRefreshToken, TokenType.REFRESH_TOKEN)).thenReturn("testuser");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(mockUser));
        when(redisTemplate.hasKey("blacklist:" + oldRefreshToken)).thenReturn(false);

        // Mock extractClaim: JWT has version 0, but DB has version 1 (from setUp)
        when(jwtService.extractClaim(eq(oldRefreshToken), eq(TokenType.REFRESH_TOKEN), any())).thenReturn(0);

        // Act & Assert
        assertThrows(AccessForbiddenException.class, () -> authenticationService.refreshToken(oldRefreshToken));
    }

    // ==========================================
    // TESTS FOR LOGOUT() & LOGOUTALLDEVICES()
    // ==========================================

    @Test
    void testLogout_Success_WithRemainingTime() {
        // Arrange
        String mockToken = "mockToken";
        when(jwtService.getRemainingTime(mockToken, TokenType.ACCESS_TOKEN)).thenReturn(5000L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // Act
        authenticationService.logout(mockToken, TokenType.ACCESS_TOKEN);

        // Assert
        verify(valueOperations).set(eq("blacklist:mockToken"), eq("logged_out"), eq(5000L),
                eq(java.util.concurrent.TimeUnit.MILLISECONDS));
    }

    @Test
    void testLogout_Success_NoRemainingTime() {
        // Arrange
        String mockToken = "mockToken";
        when(jwtService.getRemainingTime(mockToken, TokenType.ACCESS_TOKEN)).thenReturn(0L);

        // Act
        authenticationService.logout(mockToken, TokenType.ACCESS_TOKEN);

        // Assert
        // redisTemplate.opsForValue() shouldn't be called
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void testLogoutAllDevices_Success() {
        // Arrange
        String username = "testuser";
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(mockUser));
        when(cacheManager.getCache("user_details")).thenReturn(userCache);

        Integer initialVersion = mockUser.getTokenVersion(); // which is 1 from setUp

        // Act
        authenticationService.logoutAllDevices(username);

        // Assert
        assertEquals(initialVersion + 1, mockUser.getTokenVersion());
        verify(userRepository).save(mockUser);
        verify(userCache).evict(username);
    }

    @Test
    void testLogoutAllDevices_UserNotFound_ThrowsException() {
        // Arrange
        String username = "unknownuser";
        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> authenticationService.logoutAllDevices(username));
        verify(userRepository, never()).save(any());
    }

    // ==========================================
    // TESTS FOR OTP FLOWS (Register & Password)
    // ==========================================

    @Test
    void testVerifyUser_Success() {
        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setEmail("test@example.com");
        request.setOtp("123456");

        RegisterData data = RegisterData.builder()
                .username("testuser")
                .email("test@example.com")
                .password("encoded")
                .otp("123456")
                .build();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("register:test@example.com")).thenReturn(data);
        when(userRepository.existsByUsername("testuser")).thenReturn(false);
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);

        RoleEntity role = new RoleEntity();
        role.setId(1L);
        role.setName("USER");
        when(roleRepository.findByName("USER")).thenReturn(Optional.of(role));

        authenticationService.verifyUser(request);

        verify(userRepository).save(any(UserEntity.class));
        verify(cuckooFilterService).add(anyString(), eq("testuser"));
        verify(redisTemplate).delete("register:test@example.com");
    }

    @Test
    void testVerifyUser_InvalidOtp() {
        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setEmail("test@example.com");
        request.setOtp("wrong");

        RegisterData data = RegisterData.builder().otp("123456").build();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("register:test@example.com")).thenReturn(data);

        assertThrows(InvalidOtpException.class, () -> authenticationService.verifyUser(request));
    }

    @Test
    void testResendOtp_Cooldown60s() {
        when(redisTemplate.hasKey("cooldown:resend:test@example.com")).thenReturn(true);
        assertThrows(ResourceConflictException.class, () -> authenticationService.resendOtp("test@example.com"));
    }

    @Test
    void testResendOtp_Success() {
        when(redisTemplate.hasKey("cooldown:resend:test@example.com")).thenReturn(false);
        RegisterData data = RegisterData.builder().username("testuser").email("test@example.com").build();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("register:test@example.com")).thenReturn(data);

        authenticationService.resendOtp("test@example.com");

        verify(valueOperations).set(eq("cooldown:resend:test@example.com"), eq("1"), eq(60L),
                eq(java.util.concurrent.TimeUnit.SECONDS));
        verify(emailKafkaProducer).sendOtpEmailTask(eq("test@example.com"), eq("testuser"), anyString());
    }

    @Test
    void testInitiateForgotPassword_Success() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(mockUser));
        mockUser.setUserStatus(UserStatus.ACTIVE);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        authenticationService.initiateForgotPassword("test@example.com");

        verify(valueOperations).set(startsWith("otp:PASSWORD_RESET:testuser"), anyString(), anyLong(),
                eq(java.util.concurrent.TimeUnit.MINUTES));
        verify(emailKafkaProducer).sendOtpEmailTask(eq("test@example.com"), eq("testuser"), anyString());
    }

    @Test
    void testVerifyPasswordReset_MaxAttemptsReached() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(mockUser));
        mockUser.setUserStatus(UserStatus.ACTIVE);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("otp:PASSWORD_RESET:testuser")).thenReturn("123456");
        when(valueOperations.increment("otp:PASSWORD_RESET:testuser:attempts")).thenReturn(5L);

        assertThrows(InvalidOtpException.class,
                () -> authenticationService.verifyPasswordReset("test@example.com", "wrongotp", "newPass"));

        verify(redisTemplate).delete("otp:PASSWORD_RESET:testuser");
    }

    @Test
    void testVerifyPasswordReset_Success() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(mockUser));
        mockUser.setUserStatus(UserStatus.ACTIVE);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("otp:PASSWORD_RESET:testuser")).thenReturn("123456");
        when(passwordEncoder.encode("newPass")).thenReturn("encodedNew");

        authenticationService.verifyPasswordReset("test@example.com", "123456", "newPass");

        assertEquals("encodedNew", mockUser.getPassword());
        assertEquals(2, mockUser.getTokenVersion());
        verify(userRepository).save(mockUser);
        verify(redisTemplate).delete("otp:PASSWORD_RESET:testuser");
    }

    @Test
    void testCreateUser_UsernameExists_ThrowsResourceConflictException() {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername("existinguser");
        request.setEmail("newuser@example.com");

        when(cuckooFilterService.exists(anyString(), eq("newuser@example.com"))).thenReturn(false);
        when(cuckooFilterService.exists(anyString(), eq("existinguser"))).thenReturn(true);
        when(userRepository.existsByUsername("existinguser")).thenReturn(true);

        assertThrows(ResourceConflictException.class, () -> authenticationService.createUser(request));
    }

    @Test
    void testCreateUser_RoleNotFound_ThrowsResourceNotFoundException() {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername("newuser");
        request.setEmail("newuser@example.com");
        request.setPassword("password123");

        when(cuckooFilterService.exists(anyString(), eq("newuser@example.com"))).thenReturn(false);
        when(cuckooFilterService.exists(anyString(), eq("newuser"))).thenReturn(false);
        when(roleRepository.findByName("USER")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> authenticationService.createUser(request));
    }

    @Test
    void testVerifyUser_OtpExpired() {
        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setEmail("test@example.com");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("register:test@example.com")).thenReturn(null);
        assertThrows(ResourceNotFoundException.class, () -> authenticationService.verifyUser(request));
    }

    @Test
    void testVerifyUser_AlreadyRegistered() {
        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setEmail("test@example.com");
        request.setOtp("123456");

        RegisterData data = RegisterData.builder().username("testuser").email("test@example.com").otp("123456").build();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("register:test@example.com")).thenReturn(data);
        when(userRepository.existsByUsername("testuser")).thenReturn(true);

        assertThrows(ResourceConflictException.class, () -> authenticationService.verifyUser(request));
    }

    @Test
    void testVerifyUser_RoleUserMissing() {
        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setEmail("test@example.com");
        request.setOtp("123456");

        RegisterData data = RegisterData.builder().username("testuser").email("test@example.com").password("pwd")
                .otp("123456").build();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("register:test@example.com")).thenReturn(data);
        when(userRepository.existsByUsername("testuser")).thenReturn(false);
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(roleRepository.findByName("USER")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> authenticationService.verifyUser(request));
    }

    @Test
    void testResendOtp_DataNull_UserActive() {
        when(redisTemplate.hasKey("cooldown:resend:test@example.com")).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("register:test@example.com")).thenReturn(null);

        mockUser.setUserStatus(UserStatus.ACTIVE);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(mockUser));

        assertThrows(ResourceConflictException.class, () -> authenticationService.resendOtp("test@example.com"));
    }

    @Test
    void testResendOtp_DataNull_UserInactive() {
        when(redisTemplate.hasKey("cooldown:resend:test@example.com")).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("register:test@example.com")).thenReturn(null);

        mockUser.setUserStatus(UserStatus.INACTIVE);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(mockUser));

        assertThrows(ResourceConflictException.class, () -> authenticationService.resendOtp("test@example.com"));
    }

    @Test
    void testResendOtp_DataNull_UserNotFound() {
        when(redisTemplate.hasKey("cooldown:resend:test@example.com")).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("register:test@example.com")).thenReturn(null);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> authenticationService.resendOtp("test@example.com"));
    }

    @Test
    void testInitiateForgotPassword_UserInactive() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(mockUser));
        mockUser.setUserStatus(UserStatus.INACTIVE);
        assertThrows(AccessForbiddenException.class,
                () -> authenticationService.initiateForgotPassword("test@example.com"));
    }

    @Test
    void testVerifyPasswordReset_UserInactive() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(mockUser));
        mockUser.setUserStatus(UserStatus.INACTIVE);
        assertThrows(AccessForbiddenException.class,
                () -> authenticationService.verifyPasswordReset("test@example.com", "123456", "newPass"));
    }

    @Test
    void testVerifyPasswordReset_OtpExpired() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(mockUser));
        mockUser.setUserStatus(UserStatus.ACTIVE);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("otp:PASSWORD_RESET:testuser")).thenReturn(null);

        assertThrows(InvalidOtpException.class,
                () -> authenticationService.verifyPasswordReset("test@example.com", "123456", "newPass"));
    }

    @Test
    void testResendForgotPasswordOtp_Success() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(mockUser));
        mockUser.setUserStatus(UserStatus.ACTIVE);

        when(redisTemplate.hasKey("cooldown:resend:testuser")).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("otp:PASSWORD_RESET:testuser")).thenReturn("123456");

        authenticationService.resendForgotPasswordOtp("test@example.com");

        verify(valueOperations).set(eq("cooldown:resend:testuser"), eq("1"), eq(60L),
                eq(java.util.concurrent.TimeUnit.SECONDS));
        verify(emailKafkaProducer).sendOtpEmailTask(eq("test@example.com"), eq("testuser"), anyString());
    }

    @Test
    void testResendForgotPasswordOtp_UserNotFound() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> authenticationService.resendForgotPasswordOtp("test@example.com"));
    }

    @Test
    void testResendForgotPasswordOtp_UserInactive() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(mockUser));
        mockUser.setUserStatus(UserStatus.INACTIVE);
        assertThrows(AccessForbiddenException.class,
                () -> authenticationService.resendForgotPasswordOtp("test@example.com"));
    }

    @Test
    void testResendForgotPasswordOtp_Cooldown() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(mockUser));
        mockUser.setUserStatus(UserStatus.ACTIVE);
        when(redisTemplate.hasKey("cooldown:resend:testuser")).thenReturn(true);
        assertThrows(ResourceConflictException.class,
                () -> authenticationService.resendForgotPasswordOtp("test@example.com"));
    }

    @Test
    void testResendForgotPasswordOtp_ReqExpired() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(mockUser));
        mockUser.setUserStatus(UserStatus.ACTIVE);
        when(redisTemplate.hasKey("cooldown:resend:testuser")).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("otp:PASSWORD_RESET:testuser")).thenReturn(null);

        assertThrows(ResourceNotFoundException.class,
                () -> authenticationService.resendForgotPasswordOtp("test@example.com"));
    }
}
