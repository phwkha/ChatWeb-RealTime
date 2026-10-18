package com.web.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.controller.request.RoleRequest;
import com.web.backend.controller.response.PermissionResponse;
import com.web.backend.controller.response.RoleResponse;
import com.web.backend.exception.custom.AccessForbiddenException;
import com.web.backend.exception.custom.ResourceConflictException;
import com.web.backend.exception.custom.ResourceNotFoundException;
import com.web.backend.mapper.UserMapper;
import com.web.backend.model.postgres.PermissionEntity;
import com.web.backend.model.postgres.RoleEntity;
import com.web.backend.repository.PermissionRepository;
import com.web.backend.repository.RoleRepository;
import com.web.backend.repository.UserRepository;
import com.web.backend.service.impl.RoleServiceImpl;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PermissionRepository permissionRepository;
    @Mock
    private UserMapper userMapper;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private RoleServiceImpl roleService;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messageSource = mock(ResourceBundleMessageSource.class);
        lenient().when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Mocked Error Message");
        Translator.setStaticMessageSource(messageSource);

        Authentication authentication = mock(Authentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        GrantedAuthority auth1 = new SimpleGrantedAuthority("P1");
        GrantedAuthority auth2 = new SimpleGrantedAuthority("P2");
        lenient().doReturn(List.of(auth1, auth2)).when(authentication).getAuthorities();
    }

    @Test
    void testGetAllRoles() {
        RoleEntity role = new RoleEntity();
        when(roleRepository.findAll()).thenReturn(List.of(role));
        RoleResponse mockRoleResponse = mock(RoleResponse.class);
        when(userMapper.toRoleResponse(role)).thenReturn(mockRoleResponse);

        List<RoleResponse> result = roleService.getAllRoles();
        assertEquals(1, result.size());
    }

    @Test
    void testGetAllPermissions() {
        PermissionEntity perm = new PermissionEntity();
        perm.setName("P1");
        when(permissionRepository.findAll()).thenReturn(List.of(perm));
        PermissionResponse mockPermissionResponse = mock(PermissionResponse.class);
        when(userMapper.toPermissionResponse(perm)).thenReturn(mockPermissionResponse);

        List<PermissionResponse> result = roleService.getAllPermissions();
        assertEquals(1, result.size());
    }

    @Test
    void testCreateRole_Success() {
        RoleRequest req = new RoleRequest();
        req.setName("MODERATOR");
        req.setDescription("Moderator Role");
        req.setPermissionIds(java.util.List.of(1L, 2L));

        when(roleRepository.findByNameIgnoreCase("MODERATOR")).thenReturn(Optional.empty());
        PermissionEntity p1 = new PermissionEntity();
        p1.setId(1L);
        p1.setName("P1");
        PermissionEntity p2 = new PermissionEntity();
        p2.setId(2L);
        p2.setName("P2");
        when(permissionRepository.findAllById(java.util.List.of(1L, 2L))).thenReturn(List.of(p1, p2));

        RoleEntity savedRole = new RoleEntity();
        when(roleRepository.save(any(RoleEntity.class))).thenReturn(savedRole);
        RoleResponse mockRoleResponse = mock(RoleResponse.class);
        when(userMapper.toRoleResponse(savedRole)).thenReturn(mockRoleResponse);

        assertNotNull(roleService.createRole(req));
        verify(roleRepository).save(any(RoleEntity.class));
    }

    @Test
    void testCreateRole_WithoutPermissions() {
        RoleRequest req = new RoleRequest();
        req.setName("GUEST");
        req.setDescription("Guest Role");
        req.setPermissionIds(null);

        when(roleRepository.findByNameIgnoreCase("GUEST")).thenReturn(Optional.empty());
        when(roleRepository.save(any(RoleEntity.class))).thenReturn(new RoleEntity());
        RoleResponse mockRoleResponse = mock(RoleResponse.class);
        when(userMapper.toRoleResponse(any())).thenReturn(mockRoleResponse);

        assertNotNull(roleService.createRole(req));
        verify(permissionRepository, never()).findAllById(any());
    }

    @Test
    void testCreateRole_Exists() {
        RoleRequest req = new RoleRequest();
        req.setName("MODERATOR");
        when(roleRepository.findByNameIgnoreCase("MODERATOR")).thenReturn(Optional.of(new RoleEntity()));

        assertThrows(ResourceConflictException.class, () -> roleService.createRole(req));
    }

    @Test
    void testCreateRole_SystemRoleAdmin_ThrowsAccessForbidden() {
        RoleRequest req = new RoleRequest();
        req.setName("admin");

        assertThrows(AccessForbiddenException.class, () -> roleService.createRole(req));
        verify(roleRepository, never()).save(any());
    }

    @Test
    void testCreateRole_SystemRoleUser_ThrowsAccessForbidden() {
        RoleRequest req = new RoleRequest();
        req.setName("User");

        assertThrows(AccessForbiddenException.class, () -> roleService.createRole(req));
        verify(roleRepository, never()).save(any());
    }

    @Test
    void testUpdateRole_Success() {
        RoleRequest req = new RoleRequest();
        req.setName("NEW_MODERATOR");
        req.setPermissionIds(java.util.List.of(1L));

        RoleEntity role = new RoleEntity();
        role.setId(1L);
        role.setName("OLD_MODERATOR");
        when(roleRepository.findById(1L)).thenReturn(Optional.of(role));
        when(roleRepository.findByNameIgnoreCase("NEW_MODERATOR")).thenReturn(Optional.empty());

        PermissionEntity p1 = new PermissionEntity();
        p1.setName("P1");
        when(permissionRepository.findAllById(java.util.List.of(1L))).thenReturn(List.of(p1));
        when(roleRepository.save(role)).thenReturn(role);
        RoleResponse mockRoleResponse = mock(RoleResponse.class);
        when(userMapper.toRoleResponse(role)).thenReturn(mockRoleResponse);

        assertNotNull(roleService.updateRole(1L, req));
    }

    @Test
    void testUpdateRole_SystemRoleAdmin_ThrowsAccessForbidden() {
        RoleRequest req = new RoleRequest();
        req.setName("CUSTOM_NAME");

        RoleEntity role = new RoleEntity();
        role.setId(1L);
        role.setName("admin");
        when(roleRepository.findById(1L)).thenReturn(Optional.of(role));

        assertThrows(AccessForbiddenException.class, () -> roleService.updateRole(1L, req));
        verify(roleRepository, never()).save(any());
    }

    @Test
    void testUpdateRole_RenameToSystemRole_ThrowsAccessForbidden() {
        RoleRequest req = new RoleRequest();
        req.setName("Admin");

        RoleEntity role = new RoleEntity();
        role.setId(1L);
        role.setName("MODERATOR");
        when(roleRepository.findById(1L)).thenReturn(Optional.of(role));

        assertThrows(AccessForbiddenException.class, () -> roleService.updateRole(1L, req));
        verify(roleRepository, never()).save(any());
    }

    @Test
    void testUpdateRole_DuplicateName_ThrowsResourceConflict() {
        RoleRequest req = new RoleRequest();
        req.setName("SUPPORT");

        RoleEntity role = new RoleEntity();
        role.setId(1L);
        role.setName("MODERATOR");

        RoleEntity existing = new RoleEntity();
        existing.setId(2L);
        existing.setName("support");

        when(roleRepository.findById(1L)).thenReturn(Optional.of(role));
        when(roleRepository.findByNameIgnoreCase("SUPPORT")).thenReturn(Optional.of(existing));

        assertThrows(ResourceConflictException.class, () -> roleService.updateRole(1L, req));
        verify(roleRepository, never()).save(any());
    }

    @Test
    void testUpdateRole_NotFound() {
        RoleRequest req = new RoleRequest();
        when(roleRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> roleService.updateRole(1L, req));
    }

    @Test
    void testUpdateRole_WithoutPermissions() {
        RoleRequest req = new RoleRequest();
        req.setName("NEW_MODERATOR");
        req.setPermissionIds(null);
        RoleEntity role = new RoleEntity();
        role.setId(1L);
        role.setName("MODERATOR");

        when(roleRepository.findById(1L)).thenReturn(Optional.of(role));
        when(roleRepository.findByNameIgnoreCase("NEW_MODERATOR")).thenReturn(Optional.empty());
        when(roleRepository.save(role)).thenReturn(role);
        RoleResponse mockRoleResponse = mock(RoleResponse.class);
        when(userMapper.toRoleResponse(role)).thenReturn(mockRoleResponse);

        assertNotNull(roleService.updateRole(1L, req));
        verify(permissionRepository, never()).findAllById(any());
    }

    @Test
    void testDeleteRole_Success() {
        RoleEntity role = new RoleEntity();
        role.setId(1L);
        role.setName("MODERATOR");
        when(roleRepository.findById(1L)).thenReturn(Optional.of(role));
        when(userRepository.existsByRole(role)).thenReturn(false);

        roleService.deleteRole(1L);

        verify(roleRepository).delete(role);
    }

    @Test
    void testDeleteRole_SystemRoleAdmin_ThrowsAccessForbidden() {
        RoleEntity role = new RoleEntity();
        role.setId(1L);
        role.setName("admin");
        when(roleRepository.findById(1L)).thenReturn(Optional.of(role));

        assertThrows(AccessForbiddenException.class, () -> roleService.deleteRole(1L));
        verify(roleRepository, never()).delete(any());
    }

    @Test
    void testDeleteRole_SystemRoleUser_ThrowsAccessForbidden() {
        RoleEntity role = new RoleEntity();
        role.setId(2L);
        role.setName("User");
        when(roleRepository.findById(2L)).thenReturn(Optional.of(role));

        assertThrows(AccessForbiddenException.class, () -> roleService.deleteRole(2L));
        verify(roleRepository, never()).delete(any());
    }

    @Test
    void testDeleteRole_NotFound() {
        when(roleRepository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> roleService.deleteRole(1L));
    }

    @Test
    void testDeleteRole_InUse() {
        RoleEntity role = new RoleEntity();
        role.setId(1L);
        role.setName("MODERATOR");
        when(roleRepository.findById(1L)).thenReturn(Optional.of(role));
        when(userRepository.existsByRole(role)).thenReturn(true);

        assertThrows(ResourceConflictException.class, () -> roleService.deleteRole(1L));
        verify(roleRepository, never()).delete(any());
    }
}
