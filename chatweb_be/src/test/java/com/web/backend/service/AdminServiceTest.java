package com.web.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.web.backend.common.UserStatus;
import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.controller.request.AddressRequest;
import com.web.backend.controller.request.AdminCreateUserRequest;
import com.web.backend.controller.request.AdminUpdateUserRequest;
import com.web.backend.controller.response.AddressResponse;
import com.web.backend.controller.response.PageResponse;
import com.web.backend.controller.response.UserDetailResponse;
import com.web.backend.controller.response.UserResponse;
import com.web.backend.controller.response.UserSummaryResponse;
import com.web.backend.exception.custom.AccessForbiddenException;
import com.web.backend.exception.custom.ResourceConflictException;
import com.web.backend.exception.custom.ResourceNotFoundException;
import com.web.backend.mapper.UserMapper;
import com.web.backend.model.postgres.AddressEntity;
import com.web.backend.model.postgres.RoleEntity;
import com.web.backend.model.postgres.UserEntity;
import com.web.backend.repository.MessageRepository;
import com.web.backend.repository.RoleRepository;
import com.web.backend.repository.UserRepository;
import com.web.backend.service.impl.AdminServiceImpl;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private UserMapper userMapper;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private StorageService storageService;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ZSetOperations<String, Object> zSetOperations;

    @InjectMocks
    private AdminServiceImpl adminService;

    private UserEntity activeUser;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messageSource = mock(ResourceBundleMessageSource.class);
        lenient().when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Mocked Error Message");
        Translator.setStaticMessageSource(messageSource);

        activeUser = new UserEntity();
        activeUser.setUsername("testuser");
        activeUser.setEmail("test@example.com");
        activeUser.setUserStatus(UserStatus.ACTIVE);
    }

    @Test
    void testGetOnlineUsers_Empty() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.reverseRange("online_users", 0, 9)).thenReturn(Set.of());
        when(zSetOperations.size("online_users")).thenReturn(0L);

        PageResponse<UserSummaryResponse> res = adminService.getOnlineUsers(0, 10);
        assertEquals(0, res.getTotalElements());
    }

    @Test
    void testGetOnlineUsers_Success() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.reverseRange("online_users", 0, 9)).thenReturn(Set.of("testuser"));
        when(zSetOperations.size("online_users")).thenReturn(1L);

        when(userRepository.findByUsernameIn(anyList())).thenReturn(List.of(activeUser));
        when(userMapper.toUserSummaryResponse(activeUser))
                .thenReturn(org.mockito.Mockito.mock(UserSummaryResponse.class));

        PageResponse<UserSummaryResponse> res = adminService.getOnlineUsers(0, 10);
        assertEquals(1, res.getTotalElements());
        assertTrue(activeUser.isOnline());
    }

    @Test
    void testGetAllUsers_Success() {
        Page<UserEntity> page = new PageImpl<>(List.of(activeUser));
        when(userRepository.findAllByUserStatusNot(eq(UserStatus.INACTIVE), any(Pageable.class))).thenReturn(page);
        when(userMapper.toUserSummaryResponse(activeUser))
                .thenReturn(org.mockito.Mockito.mock(UserSummaryResponse.class));

        PageResponse<UserSummaryResponse> res = adminService.getAllUsers(0, 10, "id:asc");
        assertEquals(1, res.getTotalElements());
    }

    @Test
    void testGetUserByUsername_Success() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(activeUser));
        when(userMapper.toUserDetailResponse(activeUser)).thenReturn(new UserDetailResponse());

        assertNotNull(adminService.getUserByUsername("testuser"));
    }

    @Test
    void testGetUserByUsername_Inactive() {
        activeUser.setUserStatus(UserStatus.INACTIVE);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(activeUser));
        assertThrows(ResourceNotFoundException.class, () -> adminService.getUserByUsername("testuser"));
    }

    @Test
    void testAdminCreateUser_Success() {
        AdminCreateUserRequest req = new AdminCreateUserRequest();
        req.setUsername("newuser");
        req.setEmail("new@example.com");
        req.setPassword("pass");
        req.setRoleId(1L);

        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(userMapper.toEntity(req)).thenReturn(new UserEntity());
        when(passwordEncoder.encode("pass")).thenReturn("encoded");
        when(roleRepository.findById(1L)).thenReturn(Optional.of(new RoleEntity()));
        when(userRepository.save(any(UserEntity.class))).thenReturn(new UserEntity());
        when(userMapper.toUserResponse(any())).thenReturn(new UserResponse());

        assertNotNull(adminService.adminCreateUser(req));
    }

    @Test
    void testAdminCreateUser_UsernameExists() {
        AdminCreateUserRequest req = new AdminCreateUserRequest();
        req.setUsername("newuser");
        when(userRepository.existsByUsername("newuser")).thenReturn(true);
        assertThrows(ResourceConflictException.class, () -> adminService.adminCreateUser(req));
    }

    @Test
    void testAdminCreateUser_EmailExists() {
        AdminCreateUserRequest req = new AdminCreateUserRequest();
        req.setUsername("newuser");
        req.setEmail("new@example.com");
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("new@example.com")).thenReturn(true);
        assertThrows(ResourceConflictException.class, () -> adminService.adminCreateUser(req));
    }

    @Test
    void testLockUser() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(activeUser));
        when(userRepository.save(activeUser)).thenReturn(activeUser);

        adminService.lockUser("testuser");
        assertEquals(UserStatus.LOCKED, activeUser.getUserStatus());
        assertFalse(activeUser.isOnline());
    }

    @Test
    void testUnlockUser() {
        activeUser.setUserStatus(UserStatus.LOCKED);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(activeUser));
        when(userRepository.save(activeUser)).thenReturn(activeUser);

        adminService.unlockUser("testuser");
        assertEquals(UserStatus.ACTIVE, activeUser.getUserStatus());
    }

    @Test
    void testUnlockUser_AlreadyActive() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(activeUser));
        when(userMapper.toUserResponse(activeUser)).thenReturn(new UserResponse());
        adminService.unlockUser("testuser");
        verify(userRepository, never()).save(any());
    }

    @Test
    void testDeleteAvatar() {
        activeUser.setAvatar("avatar.jpg");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(activeUser));
        adminService.deleteAvatar("testuser");
        verify(storageService).delete("avatar.jpg", "avatars");
        assertNull(activeUser.getAvatar());
    }

    @Test
    void testAdminUpdateUser_Success() {
        AdminUpdateUserRequest req = new AdminUpdateUserRequest();
        req.setEmail("new@example.com");
        req.setRoleId(1L);

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(activeUser));
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(roleRepository.findById(1L)).thenReturn(Optional.of(new RoleEntity()));
        when(userRepository.save(activeUser)).thenReturn(activeUser);

        adminService.adminUpdateUser("testuser", req);
        verify(userMapper).updateAdminUserFromRequest(req, activeUser);
    }

    @Test
    void testAdminUpdateUser_EmailExists() {
        AdminUpdateUserRequest req = new AdminUpdateUserRequest();
        req.setEmail("new@example.com");

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(activeUser));
        when(userRepository.existsByEmail("new@example.com")).thenReturn(true);

        assertThrows(ResourceConflictException.class, () -> adminService.adminUpdateUser("testuser", req));
    }

    @Test
    void testAdminDeleteUser_SoftDelete() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(activeUser));
        when(messageRepository.existsBySenderOrRecipient("testuser")).thenReturn(true);

        adminService.adminDeleteUser("testuser", "admin");

        assertEquals(UserStatus.INACTIVE, activeUser.getUserStatus());
        verify(userRepository).save(activeUser);
        verify(userRepository, never()).delete(activeUser);
    }

    @Test
    void testAdminDeleteUser_HardDelete() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(activeUser));
        when(messageRepository.existsBySenderOrRecipient("testuser")).thenReturn(false);

        adminService.adminDeleteUser("testuser", "admin");

        verify(userRepository).delete(activeUser);
    }

    @Test
    void testAdminGetAllAddresses() {
        AddressEntity addr = new AddressEntity();
        addr.setId(1L);
        activeUser.addAddress(addr);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(activeUser));

        List<AddressResponse> res = adminService.adminGetAllAddresses("testuser");
        assertEquals(1, res.size());
    }

    @Test
    void testAdminGetAddressById() {
        AddressEntity addr = new AddressEntity();
        addr.setId(1L);
        activeUser.addAddress(addr);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(activeUser));
        when(userMapper.toAddressResponse(addr)).thenReturn(new AddressResponse());

        assertNotNull(adminService.adminGetAddressById("testuser", 1L));
    }

    @Test
    void testAdminGetAddressById_NotOwned() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(activeUser));
        assertThrows(AccessForbiddenException.class, () -> adminService.adminGetAddressById("testuser", 1L));
    }

    @Test
    void testAdminUpdateAddress() {
        AddressEntity addr = new AddressEntity();
        addr.setId(1L);
        activeUser.addAddress(addr);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(activeUser));

        AddressRequest req = new AddressRequest();
        adminService.adminUpdateAddress("testuser", 1L, req);

        verify(userMapper).updateAddressFromRequest(req, addr);
        verify(userRepository).save(activeUser);
    }

    @Test
    void testAdminUpdateAddress_NotOwned() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(activeUser));
        AddressRequest req = new AddressRequest();
        assertThrows(ResourceNotFoundException.class, () -> adminService.adminUpdateAddress("testuser", 1L, req));
    }

    @Test
    void testAdminDeleteAddress() {
        AddressEntity addr = new AddressEntity();
        addr.setId(1L);
        activeUser.addAddress(addr);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(activeUser));

        adminService.adminDeleteAddress("testuser", 1L);
        assertFalse(activeUser.getAddresses().contains(addr));
        verify(userRepository).save(activeUser);
    }

    @Test
    void testGetUserByUsername_NotFound() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> adminService.getUserByUsername("testuser"));
    }

    @Test
    void testLockUser_NotFound() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> adminService.lockUser("testuser"));
    }

    @Test
    void testUnlockUser_NotFound() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> adminService.unlockUser("testuser"));
    }

    @Test
    void testDeleteAvatar_NotFound() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> adminService.deleteAvatar("testuser"));
    }

    @Test
    void testAdminUpdateUser_NotFound() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> adminService.adminUpdateUser("testuser", new AdminUpdateUserRequest()));
    }

    @Test
    void testAdminDeleteUser_NotFound() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> adminService.adminDeleteUser("testuser", "admin"));
    }
}
