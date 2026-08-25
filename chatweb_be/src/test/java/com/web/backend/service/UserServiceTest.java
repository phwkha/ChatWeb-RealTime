package com.web.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import com.web.backend.common.AuthProvider;
import com.web.backend.common.UserStatus;
import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.controller.request.*;
import com.web.backend.controller.response.*;
import com.web.backend.exception.custom.*;
import com.web.backend.mapper.UserMapper;
import com.web.backend.model.postgres.AddressEntity;
import com.web.backend.model.postgres.UserEntity;
import com.web.backend.repository.AddressRepository;
import com.web.backend.repository.MessageRepository;
import com.web.backend.repository.UserRepository;
import com.web.backend.repository.FriendshipRepository;
import org.springframework.context.ApplicationEventPublisher;
import com.web.backend.service.impl.UserServiceImpl;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private AddressRepository addressRepository;
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private EmailService emailService;
    @Mock
    private StorageService storageService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private UserMapper userMapper;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private CuckooFilterService cuckooFilterService;
    @Mock
    private FriendshipRepository friendshipRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private UserServiceImpl userService;

    private UserEntity activeUser;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messageSource = mock(ResourceBundleMessageSource.class);
        lenient().when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Mocked Error Message");
        Translator.setStaticMessageSource(messageSource);

        ReflectionTestUtils.setField(userService, "expirationMinutes", 5);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        activeUser = new UserEntity();
        activeUser.setUsername("testuser");
        activeUser.setEmail("test@example.com");
        activeUser.setPassword("encoded_pw");
        activeUser.setUserStatus(UserStatus.ACTIVE);
        activeUser.setAuthProvider(AuthProvider.LOCAL);
    }

    @Test
    void testGetMe_Success() {
        when(userRepository.findWithAuthoritiesByUsername("testuser")).thenReturn(Optional.of(activeUser));
        when(userMapper.toUserResponse(activeUser)).thenReturn(new UserResponse());

        assertNotNull(userService.getMe("testuser"));
    }

    @Test
    void testGetMe_Inactive_ThrowsException() {
        activeUser.setUserStatus(UserStatus.INACTIVE);
        when(userRepository.findWithAuthoritiesByUsername("testuser")).thenReturn(Optional.of(activeUser));

        assertThrows(ResourceNotFoundException.class, () -> userService.getMe("testuser"));
    }

    @Test
    void testUpdateAvatar_Success() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(activeUser));
        MultipartFile file = mock(MultipartFile.class);
        when(storageService.uploadAvatar(file)).thenReturn("http://new-avatar.jpg");
        when(userRepository.save(any())).thenReturn(activeUser);

        String url = userService.updateAvatar("testuser", file);
        assertEquals("http://new-avatar.jpg", url);
        assertEquals("http://new-avatar.jpg", activeUser.getAvatar());
    }

    @Test
    void testInitiateEmailChange_Success() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("password", "encoded_pw")).thenReturn(true);
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);

        userService.initiateEmailChange("testuser", "new@example.com", "password");

        verify(valueOperations).set(contains("otp:EMAIL_CHANGE:testuser"), contains("new@example.com"), eq(5L),
                eq(java.util.concurrent.TimeUnit.MINUTES));
        verify(emailService).sendOtpEmail(eq("new@example.com"), eq("testuser"), anyString());
    }

    @Test
    void testInitiateEmailChange_WrongPassword() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("wrong_pw", "encoded_pw")).thenReturn(false);

        assertThrows(InvalidPasswordException.class,
                () -> userService.initiateEmailChange("testuser", "new@example.com", "wrong_pw"));
    }

    @Test
    void testVerifyEmailChange_Success() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(activeUser));
        when(valueOperations.get("otp:EMAIL_CHANGE:testuser")).thenReturn("123456:new@example.com");

        userService.verifyEmailChange("testuser", "123456");

        assertEquals("new@example.com", activeUser.getEmail());
        verify(cuckooFilterService).delete(anyString(), eq("test@example.com"));
        verify(cuckooFilterService).add(anyString(), eq("new@example.com"));
        verify(userRepository).save(activeUser);
        verify(redisTemplate).delete("otp:EMAIL_CHANGE:testuser");
    }

    @Test
    void testVerifyEmailChange_WrongOtp_IncrementsAttempts() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(activeUser));
        when(valueOperations.get("otp:EMAIL_CHANGE:testuser")).thenReturn("123456:new@example.com");
        when(valueOperations.increment("otp:EMAIL_CHANGE:testuser:attempts")).thenReturn(1L);

        assertThrows(InvalidOtpException.class, () -> userService.verifyEmailChange("testuser", "999999"));
        verify(redisTemplate).expire(eq("otp:EMAIL_CHANGE:testuser:attempts"), eq(5L), any());
    }

    @Test
    void testChangePassword_Success() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("old_pw", "encoded_pw")).thenReturn(true);
        when(passwordEncoder.matches("new_pw", "encoded_pw")).thenReturn(false);
        when(passwordEncoder.encode("new_pw")).thenReturn("new_encoded_pw");

        userService.changePassword("testuser", "old_pw", "new_pw");

        assertEquals("new_encoded_pw", activeUser.getPassword());
        assertEquals(1, activeUser.getTokenVersion());
        verify(userRepository).save(activeUser);
    }

    @Test
    void testChangePassword_SamePassword() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("old_pw", "encoded_pw")).thenReturn(true);
        when(passwordEncoder.matches("old_pw", "encoded_pw")).thenReturn(true);

        assertThrows(PasswordMismatchException.class, () -> userService.changePassword("testuser", "old_pw", "old_pw"));
    }

    @Test
    void testDeleteUser_WithChatHistory_SoftDelete() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(activeUser));
        when(messageRepository.existsBySenderOrRecipient("testuser")).thenReturn(true);

        userService.deleteUser("testuser");

        assertEquals(UserStatus.INACTIVE, activeUser.getUserStatus());
        assertNull(activeUser.getEmail());
        assertNull(activeUser.getPhone());
        verify(userRepository).save(activeUser);
        verify(userRepository, never()).delete(activeUser);
    }

    @Test
    void testDeleteUser_NoChatHistory_HardDelete() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(activeUser));
        when(messageRepository.existsBySenderOrRecipient("testuser")).thenReturn(false);

        userService.deleteUser("testuser");

        verify(userRepository).delete(activeUser);
    }

    @Test
    void testAddAddress() {
        when(userRepository.findWithAuthoritiesByUsername("testuser")).thenReturn(Optional.of(activeUser));
        AddressRequest req = new AddressRequest();
        AddressEntity address = new AddressEntity();
        address.setId(1L);
        when(userMapper.toAddressEntity(req)).thenReturn(address);

        userService.addAddress("testuser", req);
        assertTrue(activeUser.getAddresses().contains(address));
        verify(userRepository).save(activeUser);
    }

    @Test
    void testGetProfileUser_Success() {
        when(userRepository.findWithAuthoritiesByUsername("testuser")).thenReturn(Optional.of(activeUser));
        when(userMapper.toUserDetailResponse(activeUser)).thenReturn(new UserDetailResponse());

        assertNotNull(userService.getProfileUser("testuser"));
    }

    @Test
    void testGetProfileUser_Inactive_ThrowsException() {
        activeUser.setUserStatus(UserStatus.INACTIVE);
        when(userRepository.findWithAuthoritiesByUsername("testuser")).thenReturn(Optional.of(activeUser));
        assertThrows(ResourceNotFoundException.class, () -> userService.getProfileUser("testuser"));
    }

    @Test
    void testUpdateUser_Success() {
        when(userRepository.findWithAuthoritiesByUsername("testuser")).thenReturn(Optional.of(activeUser));
        UpdateUserRequest req = new UpdateUserRequest();
        when(userRepository.save(activeUser)).thenReturn(activeUser);

        userService.updateUser("testuser", req);
        verify(userMapper).updateUserFromRequest(req, activeUser);
        verify(userRepository).save(activeUser);
    }

    @Test
    void testInitiatePhoneChange_Success() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("password", "encoded_pw")).thenReturn(true);

        userService.initiatePhoneChange("testuser", "0123456789", "password");

        verify(valueOperations).set(contains("otp:PHONE_CHANGE:testuser"), contains("0123456789"), eq(5L),
                eq(java.util.concurrent.TimeUnit.MINUTES));
        verify(emailService).sendOtpEmail(eq("test@example.com"), eq("testuser"), anyString());
    }

    @Test
    void testInitiatePhoneChange_WrongPassword() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("wrong_pw", "encoded_pw")).thenReturn(false);

        assertThrows(InvalidPasswordException.class,
                () -> userService.initiatePhoneChange("testuser", "0123456789", "wrong_pw"));
    }

    @Test
    void testInitiatePhoneChange_SocialAccount() {
        activeUser.setAuthProvider(AuthProvider.GOOGLE);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(activeUser));
        assertThrows(AccessForbiddenException.class,
                () -> userService.initiatePhoneChange("testuser", "0123456789", "password"));
    }

    @Test
    void testInitiateEmailChange_SocialAccount() {
        activeUser.setAuthProvider(AuthProvider.GOOGLE);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(activeUser));
        assertThrows(AccessForbiddenException.class,
                () -> userService.initiateEmailChange("testuser", "new@example.com", "password"));
    }

    @Test
    void testInitiateEmailChange_EmailExists() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("password", "encoded_pw")).thenReturn(true);
        when(userRepository.existsByEmail("new@example.com")).thenReturn(true);
        assertThrows(ResourceConflictException.class,
                () -> userService.initiateEmailChange("testuser", "new@example.com", "password"));
    }

    @Test
    void testVerifyPhoneChange_Success() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(activeUser));
        when(valueOperations.get("otp:PHONE_CHANGE:testuser")).thenReturn("123456:0123456789");

        userService.verifyPhoneChange("testuser", "123456");

        assertEquals("0123456789", activeUser.getPhone());
        verify(userRepository).save(activeUser);
        verify(redisTemplate).delete("otp:PHONE_CHANGE:testuser");
    }

    @Test
    void testVerifyPhoneChange_InvalidOtp() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(activeUser));
        when(valueOperations.get("otp:PHONE_CHANGE:testuser")).thenReturn(null);

        assertThrows(InvalidOtpException.class, () -> userService.verifyPhoneChange("testuser", "123456"));
    }

    @Test
    void testVerifyPhoneChange_MissingData() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(activeUser));
        when(valueOperations.get("otp:PHONE_CHANGE:testuser")).thenReturn("123456"); // Missing newPhone data

        assertThrows(InvalidDataException.class, () -> userService.verifyPhoneChange("testuser", "123456"));
    }

    @Test
    void testVerifyEmailChange_MaxAttempts() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(activeUser));
        when(valueOperations.get("otp:EMAIL_CHANGE:testuser")).thenReturn("123456:new@example.com");
        when(valueOperations.increment("otp:EMAIL_CHANGE:testuser:attempts")).thenReturn(5L);

        assertThrows(InvalidOtpException.class, () -> userService.verifyEmailChange("testuser", "999999"));
        verify(redisTemplate).delete("otp:EMAIL_CHANGE:testuser");
        verify(redisTemplate).delete("otp:EMAIL_CHANGE:testuser:attempts");
    }

    @Test
    void testResendEmailChangeOtp_Success() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(activeUser));
        when(valueOperations.get("otp:EMAIL_CHANGE:testuser")).thenReturn("111111:new@example.com");
        when(redisTemplate.hasKey("cooldown:resend:testuser")).thenReturn(false);

        userService.resendEmailChangeOtp("testuser");

        verify(valueOperations).set(contains("otp:EMAIL_CHANGE:testuser"), contains("new@example.com"), eq(5L),
                eq(java.util.concurrent.TimeUnit.MINUTES));
        verify(valueOperations).set("cooldown:resend:testuser", "1", 60L, java.util.concurrent.TimeUnit.SECONDS);
        verify(emailService).sendOtpEmail(eq("new@example.com"), eq("testuser"), anyString());
    }

    @Test
    void testResendEmailChangeOtp_Cooldown() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(activeUser));
        when(valueOperations.get("otp:EMAIL_CHANGE:testuser")).thenReturn("111111:new@example.com");
        when(redisTemplate.hasKey("cooldown:resend:testuser")).thenReturn(true);
        assertThrows(ResourceConflictException.class, () -> userService.resendEmailChangeOtp("testuser"));
    }

    @Test
    void testResendEmailChangeOtp_NotFoundInRedis() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(activeUser));
        when(valueOperations.get("otp:EMAIL_CHANGE:testuser")).thenReturn(null);
        assertThrows(ResourceNotFoundException.class, () -> userService.resendEmailChangeOtp("testuser"));
    }

    @Test
    void testResendEmailChangeOtp_MissingDataInRedis() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(activeUser));
        when(valueOperations.get("otp:EMAIL_CHANGE:testuser")).thenReturn("111111"); // Missing new email string
        assertThrows(InvalidDataException.class, () -> userService.resendEmailChangeOtp("testuser"));
    }

    @Test
    void testResendPhoneChangeOtp_Success() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(activeUser));
        when(valueOperations.get("otp:PHONE_CHANGE:testuser")).thenReturn("111111:0123456789");
        when(redisTemplate.hasKey("cooldown:resend:testuser")).thenReturn(false);

        userService.resendPhoneChangeOtp("testuser");

        verify(valueOperations).set(contains("otp:PHONE_CHANGE:testuser"), contains("0123456789"), eq(5L),
                eq(java.util.concurrent.TimeUnit.MINUTES));
        verify(emailService).sendOtpEmail(eq("test@example.com"), eq("testuser"), anyString());
    }

    @Test
    void testUpdateAddress_Success() {
        AddressEntity address = new AddressEntity();
        address.setId(1L);
        when(userRepository.findWithAuthoritiesByUsername("testuser")).thenReturn(Optional.of(activeUser));
        when(addressRepository.findByIdAndUser_Username(1L, "testuser")).thenReturn(Optional.of(address));

        AddressRequest req = new AddressRequest();
        userService.updateAddress("testuser", 1L, req);

        verify(userMapper).updateAddressFromRequest(req, address);
        verify(addressRepository).save(address);
    }

    @Test
    void testUpdateAddress_NotFound() {
        when(userRepository.findWithAuthoritiesByUsername("testuser")).thenReturn(Optional.of(activeUser));
        when(addressRepository.findByIdAndUser_Username(1L, "testuser")).thenReturn(Optional.empty());
        AddressRequest req = new AddressRequest();
        assertThrows(ResourceNotFoundException.class, () -> userService.updateAddress("testuser", 1L, req));
    }

    @Test
    void testDeleteAddress_Success() {
        AddressEntity address = new AddressEntity();
        address.setId(1L);
        activeUser.addAddress(address);
        when(userRepository.findWithAuthoritiesByUsername("testuser")).thenReturn(Optional.of(activeUser));
        when(addressRepository.findByIdAndUser_Username(1L, "testuser")).thenReturn(Optional.of(address));

        userService.deleteAddress("testuser", 1L);

        assertFalse(activeUser.getAddresses().contains(address));
        verify(addressRepository).delete(address);
    }

    @Test
    void testDeleteAddress_NotFound() {
        when(userRepository.findWithAuthoritiesByUsername("testuser")).thenReturn(Optional.of(activeUser));
        when(addressRepository.findByIdAndUser_Username(1L, "testuser")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> userService.deleteAddress("testuser", 1L));
    }

    @Test
    void testGetAllAddresses() {
        AddressEntity address = new AddressEntity();
        address.setId(1L);
        when(userRepository.existsByUsername("testuser")).thenReturn(true);
        when(addressRepository.findAllByUser_Username("testuser")).thenReturn(List.of(address));
        when(userMapper.toAddressResponse(address)).thenReturn(new AddressResponse());

        List<AddressResponse> list = userService.getAllAddresses("testuser");
        assertEquals(1, list.size());
    }

    @Test
    void testGetAddressById_Success() {
        AddressEntity address = new AddressEntity();
        address.setId(1L);
        when(userRepository.existsByUsername("testuser")).thenReturn(true);
        when(addressRepository.findByIdAndUser_Username(1L, "testuser")).thenReturn(Optional.of(address));
        when(userMapper.toAddressResponse(address)).thenReturn(new AddressResponse());

        assertNotNull(userService.getAddressById("testuser", 1L));
    }

    @Test
    void testGetAddressById_NotFound() {
        when(userRepository.existsByUsername("testuser")).thenReturn(true);
        when(addressRepository.findByIdAndUser_Username(1L, "testuser")).thenReturn(Optional.empty());
        assertThrows(AccessForbiddenException.class, () -> userService.getAddressById("testuser", 1L));
    }

    @Test
    void testUserExists() {
        when(userRepository.existsByUsername("testuser")).thenReturn(true);
        assertTrue(userService.userExists("testuser"));
    }

    @Test
    void testSetUserOnlineStatus() {

        when(friendshipRepository.findAllFriendUsernamesByUsername("testuser"))
                .thenReturn(java.util.List.of("friend1"));

        userService.setUserOnlineStatus("testuser", true);

        verify(userRepository).updateOnlineStatus("testuser", true);
        verify(eventPublisher).publishEvent(any(com.web.backend.kafka.payload.FriendPayload.class));
    }

    @Test
    void testUpdateAvatar_WithOldAvatar_Success() {
        activeUser.setAvatar("old-avatar.jpg");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(activeUser));
        MultipartFile file = mock(MultipartFile.class);
        when(storageService.uploadAvatar(file)).thenReturn("http://new-avatar.jpg");
        when(userRepository.save(any())).thenReturn(activeUser);

        String url = userService.updateAvatar("testuser", file);
        assertEquals("http://new-avatar.jpg", url);
        verify(storageService).delete("old-avatar.jpg", "avatars");
    }

    @Test
    void testUpdateAvatar_WithOldAvatar_DeleteFails() {
        activeUser.setAvatar("old-avatar.jpg");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(activeUser));
        MultipartFile file = mock(MultipartFile.class);
        when(storageService.uploadAvatar(file)).thenReturn("http://new-avatar.jpg");
        doThrow(new RuntimeException("delete failed")).when(storageService).delete(anyString(), anyString());
        when(userRepository.save(any())).thenReturn(activeUser);

        String url = userService.updateAvatar("testuser", file);
        assertEquals("http://new-avatar.jpg", url); // Should still succeed
    }

    @Test
    void testVerifyEmailChange_EmptyEmail() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(activeUser));
        when(valueOperations.get("otp:EMAIL_CHANGE:testuser")).thenReturn("123456:"); // Empty email

        assertThrows(InvalidDataException.class, () -> userService.verifyEmailChange("testuser", "123456"));
    }

    @Test
    void testUpdateAddress_NotOwned() {
        AddressEntity address = new AddressEntity();
        address.setId(2L);
        activeUser.addAddress(address); // User owns address 2
        when(userRepository.findWithAuthoritiesByUsername("testuser")).thenReturn(Optional.of(activeUser));

        AddressRequest req = new AddressRequest();
        assertThrows(ResourceNotFoundException.class, () -> userService.updateAddress("testuser", 1L, req)); // Trying
                                                                                                             // to
                                                                                                             // update 1
    }

    @Test
    void testUpdateAvatar_UserNotFound() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> userService.updateAvatar("testuser", mock(MultipartFile.class)));
    }

    @Test
    void testInitiateEmailChange_UserNotFound() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> userService.initiateEmailChange("testuser", "new@example.com", "pw"));
    }

    @Test
    void testVerifyEmailChange_UserNotFound() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> userService.verifyEmailChange("testuser", "123456"));
    }

    @Test
    void testChangePassword_UserNotFound() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> userService.changePassword("testuser", "old", "new"));
    }

    @Test
    void testDeleteUser_UserNotFound() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> userService.deleteUser("testuser"));
    }

    @Test
    void testAddAddress_UserNotFound() {
        when(userRepository.findWithAuthoritiesByUsername("testuser")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> userService.addAddress("testuser", new AddressRequest()));
    }

    @Test
    void testGetProfileUser_UserNotFound() {
        when(userRepository.findWithAuthoritiesByUsername("testuser")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> userService.getProfileUser("testuser"));
    }

    @Test
    void testUpdateUser_UserNotFound() {
        when(userRepository.findWithAuthoritiesByUsername("testuser")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> userService.updateUser("testuser", new UpdateUserRequest()));
    }

    @Test
    void testInitiatePhoneChange_UserNotFound() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> userService.initiatePhoneChange("testuser", "123456", "pw"));
    }

    @Test
    void testVerifyPhoneChange_UserNotFound() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> userService.verifyPhoneChange("testuser", "123456"));
    }

    @Test
    void testUpdateAddress_UserNotFound() {
        when(userRepository.findWithAuthoritiesByUsername("testuser")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> userService.updateAddress("testuser", 1L, new AddressRequest()));
    }

    @Test
    void testDeleteAddress_UserNotFound() {
        when(userRepository.findWithAuthoritiesByUsername("testuser")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> userService.deleteAddress("testuser", 1L));
    }

    @Test
    void testChangePassword_SocialAccount() {
        activeUser.setAuthProvider(AuthProvider.GOOGLE);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(activeUser));
        assertThrows(AccessForbiddenException.class, () -> userService.changePassword("testuser", "old", "new"));
    }

}
