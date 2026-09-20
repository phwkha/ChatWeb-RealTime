package com.web.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.List;
import java.util.Collections;

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
        when(userMapper.toUserResponse(activeUser)).thenReturn(new UserResponse());

        assertNotNull(userService.getMe(activeUser));
    }

    @Test
    void testGetMe_Inactive_ThrowsException() {
        activeUser.setUserStatus(UserStatus.INACTIVE);

        assertThrows(ResourceNotFoundException.class, () -> userService.getMe(activeUser));
    }

    @Test
    void testUpdateAvatar_Success_WithoutOldAvatar() {
        MultipartFile file = mock(MultipartFile.class);
        when(storageService.uploadAvatar(file)).thenReturn("http://new-avatar.jpg");

        String url = userService.updateAvatar(activeUser, file);
        assertEquals("http://new-avatar.jpg", url);
        verify(storageService, never()).delete(anyString(), anyString());
        verify(userRepository).updateAvatar("testuser", "http://new-avatar.jpg");
    }

    @Test
    void testInitiateEmailChange_Success() {
        when(passwordEncoder.matches("password", "encoded_pw")).thenReturn(true);
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);

        userService.initiateEmailChange(activeUser, "new@example.com", "password");

        verify(valueOperations).set(contains("otp:EMAIL_CHANGE:testuser"), contains("new@example.com"), eq(5L),
                eq(java.util.concurrent.TimeUnit.MINUTES));
        verify(emailService).sendOtpEmail(eq("new@example.com"), eq("testuser"), anyString());
    }

    @Test
    void testInitiateEmailChange_WrongPassword() {
        when(passwordEncoder.matches("wrong_pw", "encoded_pw")).thenReturn(false);

        assertThrows(InvalidPasswordException.class,
                () -> userService.initiateEmailChange(activeUser, "new@example.com", "wrong_pw"));
    }

    @Test
    void testVerifyEmailChange_Success() {
        activeUser.setEmail("test@example.com");
        when(valueOperations.get("otp:EMAIL_CHANGE:testuser")).thenReturn("123456:new@example.com");

        userService.verifyEmailChange(activeUser, "123456");

        verify(userRepository).updateEmail("testuser", "new@example.com");
        verify(cuckooFilterService).delete(anyString(), eq("test@example.com"));
        verify(cuckooFilterService).add(anyString(), eq("new@example.com"));
        verify(redisTemplate).delete("otp:EMAIL_CHANGE:testuser");
    }

    @Test
    void testVerifyEmailChange_WrongOtp_IncrementsAttempts() {
        activeUser.setEmail("test@example.com");
        when(valueOperations.get("otp:EMAIL_CHANGE:testuser")).thenReturn("123456:new@example.com");
        when(valueOperations.increment("otp:EMAIL_CHANGE:testuser:attempts")).thenReturn(1L);

        assertThrows(InvalidOtpException.class, () -> userService.verifyEmailChange(activeUser, "999999"));
        verify(redisTemplate).expire(eq("otp:EMAIL_CHANGE:testuser:attempts"), eq(5L), any());
    }

    @Test
    void testChangePassword_Success() {
        when(passwordEncoder.matches("old_pw", "encoded_pw")).thenReturn(true);
        when(passwordEncoder.matches("new_pw", "encoded_pw")).thenReturn(false);
        when(passwordEncoder.encode("new_pw")).thenReturn("new_encoded_pw");

        userService.changePassword(activeUser, "old_pw", "new_pw");

        assertEquals("new_encoded_pw", activeUser.getPassword());
        assertEquals(1, activeUser.getTokenVersion());
        verify(userRepository).save(activeUser);
    }

    @Test
    void testChangePassword_SamePassword() {
        when(passwordEncoder.matches("old_pw", "encoded_pw")).thenReturn(true);
        when(passwordEncoder.matches("old_pw", "encoded_pw")).thenReturn(true);

        assertThrows(PasswordMismatchException.class, () -> userService.changePassword(activeUser, "old_pw", "old_pw"));
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
        AddressRequest req = new AddressRequest();
        AddressEntity address = new AddressEntity();
        address.setId(1L);
        when(userMapper.toAddressEntity(req)).thenReturn(address);

        userService.addAddress(activeUser, req);
        assertEquals(activeUser, address.getUser());
        verify(addressRepository).save(address);
    }

    @Test
    void testGetProfileUser_Success() {
        when(userMapper.toUserDetailResponse(activeUser)).thenReturn(new UserDetailResponse());
        when(addressRepository.findAddressResponsesByUsername("testuser")).thenReturn(Collections.emptyList());

        assertNotNull(userService.getProfileUser(activeUser));
    }

    @Test
    void testGetProfileUser_Inactive_ThrowsException() {
        activeUser.setUserStatus(UserStatus.INACTIVE);
        assertThrows(ResourceNotFoundException.class, () -> userService.getProfileUser(activeUser));
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
        activeUser.setEmail("test@example.com");
        when(passwordEncoder.matches("password", "encoded_pw")).thenReturn(true);

        userService.initiatePhoneChange(activeUser, "0123456789", "password");

        verify(valueOperations).set(contains("otp:PHONE_CHANGE:testuser"), contains("0123456789"), eq(5L),
                eq(java.util.concurrent.TimeUnit.MINUTES));
        verify(emailService).sendOtpEmail(eq("test@example.com"), eq("testuser"), anyString());
    }

    @Test
    void testInitiatePhoneChange_WrongPassword() {
        when(passwordEncoder.matches("wrong_pw", "encoded_pw")).thenReturn(false);

        assertThrows(InvalidPasswordException.class,
                () -> userService.initiatePhoneChange(activeUser, "0123456789", "wrong_pw"));
    }

    @Test
    void testInitiatePhoneChange_SocialAccount() {
        activeUser.setAuthProvider(AuthProvider.GOOGLE);
        assertThrows(AccessForbiddenException.class,
                () -> userService.initiatePhoneChange(activeUser, "0123456789", "password"));
    }

    @Test
    void testInitiateEmailChange_SocialAccount() {
        activeUser.setAuthProvider(AuthProvider.GOOGLE);
        assertThrows(AccessForbiddenException.class,
                () -> userService.initiateEmailChange(activeUser, "new@example.com", "password"));
    }

    @Test
    void testInitiateEmailChange_EmailExists() {
        when(passwordEncoder.matches("password", "encoded_pw")).thenReturn(true);
        when(userRepository.existsByEmail("new@example.com")).thenReturn(true);
        assertThrows(ResourceConflictException.class,
                () -> userService.initiateEmailChange(activeUser, "new@example.com", "password"));
    }

    @Test
    void testVerifyPhoneChange_Success() {
        when(valueOperations.get("otp:PHONE_CHANGE:testuser")).thenReturn("123456:0123456789");

        userService.verifyPhoneChange("testuser", "123456");

        verify(userRepository).updatePhone("testuser", "0123456789");
        verify(redisTemplate).delete("otp:PHONE_CHANGE:testuser");
    }

    @Test
    void testVerifyPhoneChange_InvalidOtp() {
        when(valueOperations.get("otp:PHONE_CHANGE:testuser")).thenReturn(null);

        assertThrows(InvalidOtpException.class, () -> userService.verifyPhoneChange("testuser", "123456"));
    }

    @Test
    void testVerifyPhoneChange_MissingData() {
        when(valueOperations.get("otp:PHONE_CHANGE:testuser")).thenReturn("123456"); // Missing newPhone data

        assertThrows(InvalidDataException.class, () -> userService.verifyPhoneChange("testuser", "123456"));
    }

    @Test
    void testVerifyEmailChange_MaxAttempts() {
        activeUser.setEmail("old@example.com");
        when(valueOperations.get("otp:EMAIL_CHANGE:testuser")).thenReturn("123456:new@example.com");
        when(valueOperations.increment("otp:EMAIL_CHANGE:testuser:attempts")).thenReturn(5L);

        assertThrows(InvalidOtpException.class, () -> userService.verifyEmailChange(activeUser, "999999"));
        verify(redisTemplate).delete("otp:EMAIL_CHANGE:testuser");
        verify(redisTemplate).delete("otp:EMAIL_CHANGE:testuser:attempts");
    }

    @Test
    void testResendEmailChangeOtp_Success() {
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
        when(valueOperations.get("otp:EMAIL_CHANGE:testuser")).thenReturn("111111:new@example.com");
        when(redisTemplate.hasKey("cooldown:resend:testuser")).thenReturn(true);
        assertThrows(ResourceConflictException.class, () -> userService.resendEmailChangeOtp("testuser"));
    }

    @Test
    void testResendEmailChangeOtp_NotFoundInRedis() {
        when(valueOperations.get("otp:EMAIL_CHANGE:testuser")).thenReturn(null);
        assertThrows(ResourceNotFoundException.class, () -> userService.resendEmailChangeOtp("testuser"));
    }

    @Test
    void testResendEmailChangeOtp_MissingDataInRedis() {
        when(valueOperations.get("otp:EMAIL_CHANGE:testuser")).thenReturn("111111"); // Missing new email string
        assertThrows(InvalidDataException.class, () -> userService.resendEmailChangeOtp("testuser"));
    }

    @Test
    void testResendPhoneChangeOtp_Success() {
        activeUser.setEmail("test@example.com");
        when(valueOperations.get("otp:PHONE_CHANGE:testuser")).thenReturn("111111:0123456789");
        when(redisTemplate.hasKey("cooldown:resend:testuser")).thenReturn(false);

        userService.resendPhoneChangeOtp(activeUser);

        verify(valueOperations).set(contains("otp:PHONE_CHANGE:testuser"), contains("0123456789"), eq(5L),
                eq(java.util.concurrent.TimeUnit.MINUTES));
        verify(emailService).sendOtpEmail(eq("test@example.com"), eq("testuser"), anyString());
    }

    @Test
    void testUpdateAddress_Success() {
        AddressEntity address = new AddressEntity();
        address.setId(1L);
        when(addressRepository.findByIdAndUser_Username(1L, "testuser")).thenReturn(Optional.of(address));

        AddressRequest req = new AddressRequest();
        userService.updateAddress("testuser", 1L, req);

        verify(userMapper).updateAddressFromRequest(req, address);
        verify(addressRepository).save(address);
    }

    @Test
    void testUpdateAddress_NotFound() {
        when(addressRepository.findByIdAndUser_Username(1L, "testuser")).thenReturn(Optional.empty());
        AddressRequest req = new AddressRequest();
        assertThrows(ResourceNotFoundException.class, () -> userService.updateAddress("testuser", 1L, req));
    }

    @Test
    void testDeleteAddress_Success() {
        AddressEntity address = new AddressEntity();
        address.setId(1L);
        when(addressRepository.findByIdAndUser_Username(1L, "testuser")).thenReturn(Optional.of(address));

        userService.deleteAddress("testuser", 1L);

        verify(addressRepository).delete(address);
    }

    @Test
    void testDeleteAddress_NotFound() {
        when(addressRepository.findByIdAndUser_Username(1L, "testuser")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> userService.deleteAddress("testuser", 1L));
    }

    @Test
    void testGetAllAddresses() {
        when(addressRepository.findAddressResponsesByUsername("testuser")).thenReturn(List.of(new AddressResponse()));

        List<AddressResponse> list = userService.getAllAddresses("testuser");
        assertEquals(1, list.size());
    }

    @Test
    void testGetAddressById_Success() {
        when(addressRepository.findAddressResponseByIdAndUsername(1L, "testuser"))
                .thenReturn(Optional.of(new AddressResponse()));

        assertNotNull(userService.getAddressById("testuser", 1L));
    }

    @Test
    void testGetAddressById_NotFound() {
        when(addressRepository.findAddressResponseByIdAndUsername(1L, "testuser")).thenReturn(Optional.empty());
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
        MultipartFile file = mock(MultipartFile.class);
        when(storageService.uploadAvatar(file)).thenReturn("http://new-avatar.jpg");

        String url = userService.updateAvatar(activeUser, file);
        assertEquals("http://new-avatar.jpg", url);
        verify(storageService).delete("old-avatar.jpg", "avatars");
        verify(userRepository).updateAvatar("testuser", "http://new-avatar.jpg");
    }

    @Test
    void testUpdateAvatar_WithOldAvatar_DeleteFails() {
        activeUser.setAvatar("old-avatar.jpg");
        MultipartFile file = mock(MultipartFile.class);
        when(storageService.uploadAvatar(file)).thenReturn("http://new-avatar.jpg");
        doThrow(new RuntimeException("delete failed")).when(storageService).delete(anyString(), anyString());

        String url = userService.updateAvatar(activeUser, file);
        assertEquals("http://new-avatar.jpg", url);
        verify(userRepository).updateAvatar("testuser", "http://new-avatar.jpg");
    }

    @Test
    void testVerifyEmailChange_EmptyEmail() {
        activeUser.setEmail("old@example.com");
        when(valueOperations.get("otp:EMAIL_CHANGE:testuser")).thenReturn("123456:"); // Empty email

        assertThrows(InvalidDataException.class, () -> userService.verifyEmailChange(activeUser, "123456"));
    }

    @Test
    void testUpdateAddress_NotOwned() {
        when(addressRepository.findByIdAndUser_Username(1L, "testuser")).thenReturn(Optional.empty());

        AddressRequest req = new AddressRequest();
        assertThrows(ResourceNotFoundException.class, () -> userService.updateAddress("testuser", 1L, req));
    }

    @Test
    void testDeleteUser_UserNotFound() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> userService.deleteUser("testuser"));
    }

    @Test
    void testUpdateUser_UserNotFound() {
        when(userRepository.findWithAuthoritiesByUsername("testuser")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> userService.updateUser("testuser", new UpdateUserRequest()));
    }

    @Test
    void testUpdateAddress_UserNotFound() {
        when(addressRepository.findByIdAndUser_Username(1L, "testuser")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> userService.updateAddress("testuser", 1L, new AddressRequest()));
    }

    @Test
    void testChangePassword_SocialAccount() {
        activeUser.setAuthProvider(AuthProvider.GOOGLE);
        assertThrows(AccessForbiddenException.class, () -> userService.changePassword(activeUser, "old", "new"));
    }

}
