package com.web.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.web.backend.common.AuthProvider;
import com.web.backend.common.GenderType;
import com.web.backend.common.UserStatus;
import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.controller.request.AddressRequest;
import com.web.backend.controller.request.AdminCreateUserRequest;
import com.web.backend.controller.request.AdminSearchUserRequest;
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
import com.web.backend.repository.AddressRepository;
import com.web.backend.repository.MessageRepository;
import com.web.backend.repository.RoleRepository;
import com.web.backend.repository.UserRepository;
import com.web.backend.repository.projection.UserAvatarProjection;
import com.web.backend.service.impl.AdminServiceImpl;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private AddressRepository addressRepository;
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
        assertThat(res.getTotalElements()).isZero();
    }

    @Test
    void testGetOnlineUsers_Success() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.reverseRange("online_users", 0, 9)).thenReturn(Set.of("testuser"));
        when(zSetOperations.size("online_users")).thenReturn(1L);

        UserSummaryResponse summary = UserSummaryResponse.builder().username("testuser").build();
        when(userRepository.findSummaryByUsernameIn(anyList())).thenReturn(List.of(summary));

        PageResponse<UserSummaryResponse> res = adminService.getOnlineUsers(0, 10);
        assertThat(res.getTotalElements()).isEqualTo(1L);
        assertThat(summary.isOnline()).isTrue();
    }

    @Test
    void testSearchUsersForAdmin_WithAllFilters_Success() {
        AdminSearchUserRequest request = AdminSearchUserRequest.builder()
                .keyword("keyword")
                .role("USER")
                .status(UserStatus.ACTIVE)
                .gender(GenderType.MAN)
                .authProvider(AuthProvider.LOCAL)
                .build();
        UserResponse response = UserResponse.builder().username("testuser").build();
        Page<UserEntity> page = new PageImpl<>(List.of(activeUser));
        when(userRepository.findAll(ArgumentMatchers.<Specification<UserEntity>>any(), any(Pageable.class))).thenReturn(page);
        when(userMapper.toUserResponse(activeUser)).thenReturn(response);

        PageResponse<UserResponse> res = adminService.searchUsersForAdmin(
                request, 0, 10, "username:asc");

        assertThat(res).isNotNull();
        assertThat(res.getTotalElements()).isEqualTo(1L);
        assertThat(res.getContent().get(0).getUsername()).isEqualTo("testuser");
    }

    @Test
    void testSearchUsersForAdmin_WithoutFilters_Success() {
        UserResponse response = UserResponse.builder().username("testuser").build();
        Page<UserEntity> page = new PageImpl<>(List.of(activeUser));
        when(userRepository.findAll(ArgumentMatchers.<Specification<UserEntity>>any(), any(Pageable.class))).thenReturn(page);
        when(userMapper.toUserResponse(activeUser)).thenReturn(response);

        PageResponse<UserResponse> res = adminService.searchUsersForAdmin(
                null, 0, 10);

        assertThat(res).isNotNull();
        assertThat(res.getTotalElements()).isEqualTo(1L);
    }

    @Test
    void testGetUserByUsername_Success() {
        when(userRepository.findWithAuthoritiesByUsername("testuser")).thenReturn(Optional.of(activeUser));
        when(userMapper.toUserDetailResponse(activeUser)).thenReturn(new UserDetailResponse());

        assertThat(adminService.getUserByUsername("testuser")).isNotNull();
    }

    @Test
    void testGetUserByUsername_Inactive() {
        activeUser.setUserStatus(UserStatus.INACTIVE);
        when(userRepository.findWithAuthoritiesByUsername("testuser")).thenReturn(Optional.of(activeUser));
        assertThatThrownBy(() -> adminService.getUserByUsername("testuser")).isInstanceOf(ResourceNotFoundException.class);
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

        assertThat(adminService.adminCreateUser(req)).isNotNull();
    }

    @Test
    void testAdminCreateUser_UsernameExists() {
        AdminCreateUserRequest req = new AdminCreateUserRequest();
        req.setUsername("newuser");
        when(userRepository.existsByUsername("newuser")).thenReturn(true);
        assertThatThrownBy(() -> adminService.adminCreateUser(req)).isInstanceOf(ResourceConflictException.class);
    }

    @Test
    void testAdminCreateUser_EmailExists() {
        AdminCreateUserRequest req = new AdminCreateUserRequest();
        req.setUsername("newuser");
        req.setEmail("new@example.com");
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("new@example.com")).thenReturn(true);
        assertThatThrownBy(() -> adminService.adminCreateUser(req)).isInstanceOf(ResourceConflictException.class);
    }

    @Test
    void testLockUser() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(activeUser));
        when(userRepository.save(activeUser)).thenReturn(activeUser);

        adminService.lockUser("testuser");
        assertThat(activeUser.getUserStatus()).isEqualTo(UserStatus.LOCKED);
        assertThat(activeUser.isOnline()).isFalse();
    }

    @Test
    void testUnlockUser() {
        activeUser.setUserStatus(UserStatus.LOCKED);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(activeUser));
        when(userRepository.save(activeUser)).thenReturn(activeUser);

        adminService.unlockUser("testuser");
        assertThat(activeUser.getUserStatus()).isEqualTo(UserStatus.ACTIVE);
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
        when(userRepository.findAvatarProjectionByUsername("testuser"))
                .thenReturn(Optional.of(new UserAvatarProjection("avatar.jpg")));
        adminService.deleteAvatar("testuser");
        verify(userRepository).updateAvatar("testuser", null);
        verify(storageService).delete("avatar.jpg", "avatars");
    }

    @Test
    void testDeleteAvatar_WhenAvatarAlreadyNull() {
        when(userRepository.findAvatarProjectionByUsername("testuser"))
                .thenReturn(Optional.of(new UserAvatarProjection(null)));
        adminService.deleteAvatar("testuser");
        verify(userRepository).updateAvatar("testuser", null);
        verify(storageService, never()).delete(anyString(), anyString());
    }

    @Test
    void testAdminUpdateUser_Success() {
        AdminUpdateUserRequest req = new AdminUpdateUserRequest();
        req.setEmail("new@example.com");
        req.setRoleId(1L);

        when(userRepository.findWithAuthoritiesByUsername("testuser")).thenReturn(Optional.of(activeUser));
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

        when(userRepository.findWithAuthoritiesByUsername("testuser")).thenReturn(Optional.of(activeUser));
        when(userRepository.existsByEmail("new@example.com")).thenReturn(true);

        assertThatThrownBy(() -> adminService.adminUpdateUser("testuser", req)).isInstanceOf(ResourceConflictException.class);
    }

    @Test
    void testAdminDeleteUser_SoftDelete() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(activeUser));
        when(messageRepository.existsBySenderOrRecipient("testuser")).thenReturn(true);

        adminService.adminDeleteUser("testuser", "admin");

        assertThat(activeUser.getUserStatus()).isEqualTo(UserStatus.INACTIVE);
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
        when(userRepository.existsByUsername("testuser")).thenReturn(true);
        when(addressRepository.findAddressResponsesByUsername("testuser")).thenReturn(List.of(new AddressResponse()));

        List<AddressResponse> res = adminService.adminGetAllAddresses("testuser");
        assertThat(res).hasSize(1);
    }

    @Test
    void testAdminGetAddressById() {
        when(userRepository.existsByUsername("testuser")).thenReturn(true);
        when(addressRepository.findAddressResponseByIdAndUsername(1L, "testuser")).thenReturn(Optional.of(new AddressResponse()));

        assertThat(adminService.adminGetAddressById("testuser", 1L)).isNotNull();
    }

    @Test
    void testAdminGetAddressById_NotOwned() {
        when(userRepository.existsByUsername("testuser")).thenReturn(true);
        when(addressRepository.findAddressResponseByIdAndUsername(1L, "testuser")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> adminService.adminGetAddressById("testuser", 1L)).isInstanceOf(AccessForbiddenException.class);
    }

    @Test
    void testAdminUpdateAddress() {
        AddressEntity addr = new AddressEntity();
        addr.setId(1L);
        when(addressRepository.findByIdAndUser_Username(1L, "testuser")).thenReturn(Optional.of(addr));

        AddressRequest req = new AddressRequest();
        adminService.adminUpdateAddress("testuser", 1L, req);

        verify(userMapper).updateAddressFromRequest(req, addr);
        verify(addressRepository).save(addr);
    }

    @Test
    void testAdminUpdateAddress_NotOwned() {
        when(addressRepository.findByIdAndUser_Username(1L, "testuser")).thenReturn(Optional.empty());
        AddressRequest req = new AddressRequest();
        assertThatThrownBy(() -> adminService.adminUpdateAddress("testuser", 1L, req)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void testAdminDeleteAddress() {
        AddressEntity addr = new AddressEntity();
        addr.setId(1L);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(activeUser));
        when(addressRepository.findByIdAndUser_Username(1L, "testuser")).thenReturn(Optional.of(addr));

        adminService.adminDeleteAddress("testuser", 1L);
        verify(addressRepository).delete(addr);
    }

    @Test
    void testGetUserByUsername_NotFound() {
        when(userRepository.findWithAuthoritiesByUsername("testuser")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> adminService.getUserByUsername("testuser")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void testLockUser_NotFound() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> adminService.lockUser("testuser")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void testUnlockUser_NotFound() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> adminService.unlockUser("testuser")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void testDeleteAvatar_NotFound() {
        when(userRepository.findAvatarProjectionByUsername("testuser")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> adminService.deleteAvatar("testuser")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void testAdminUpdateUser_NotFound() {
        when(userRepository.findWithAuthoritiesByUsername("testuser")).thenReturn(Optional.empty());
        assertThatThrownBy(
                () -> adminService.adminUpdateUser("testuser", new AdminUpdateUserRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void testAdminDeleteUser_NotFound() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> adminService.adminDeleteUser("testuser", "admin"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
