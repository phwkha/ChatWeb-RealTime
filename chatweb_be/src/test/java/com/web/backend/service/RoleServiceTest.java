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

import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.controller.request.RoleRequest;
import com.web.backend.controller.response.PermissionResponse;
import com.web.backend.controller.response.RoleResponse;
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
    }

    @Test
    void testGetAllRoles() {
        RoleEntity role = new RoleEntity();
        when(roleRepository.findAll()).thenReturn(List.of(role));
        when(userMapper.toRoleResponse(role)).thenReturn(org.mockito.Mockito.mock(RoleResponse.class));

        List<RoleResponse> result = roleService.getAllRoles();
        assertEquals(1, result.size());
    }

    @Test
    void testGetAllPermissions() {
        PermissionEntity perm = new PermissionEntity();
        when(permissionRepository.findAll()).thenReturn(List.of(perm));
        when(userMapper.toPermissionResponse(perm)).thenReturn(org.mockito.Mockito.mock(PermissionResponse.class));

        List<PermissionResponse> result = roleService.getAllPermissions();
        assertEquals(1, result.size());
    }

    @Test
    void testCreateRole_Success() {
        RoleRequest req = new RoleRequest();
        req.setName("ADMIN");
        req.setDescription("Admin Role");
        req.setPermissionIds(java.util.List.of(1L, 2L));

        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.empty());
        PermissionEntity p1 = new PermissionEntity();
        p1.setId(1L);
        PermissionEntity p2 = new PermissionEntity();
        p2.setId(2L);
        when(permissionRepository.findAllById(java.util.List.of(1L, 2L))).thenReturn(List.of(p1, p2));

        RoleEntity savedRole = new RoleEntity();
        when(roleRepository.save(any(RoleEntity.class))).thenReturn(savedRole);
        when(userMapper.toRoleResponse(savedRole)).thenReturn(org.mockito.Mockito.mock(RoleResponse.class));

        assertNotNull(roleService.createRole(req));
        verify(roleRepository).save(any(RoleEntity.class));
    }

    @Test
    void testCreateRole_WithoutPermissions() {
        RoleRequest req = new RoleRequest();
        req.setName("USER");
        req.setDescription("User Role");
        req.setPermissionIds(null);

        when(roleRepository.findByName("USER")).thenReturn(Optional.empty());
        when(roleRepository.save(any(RoleEntity.class))).thenReturn(new RoleEntity());
        when(userMapper.toRoleResponse(any())).thenReturn(org.mockito.Mockito.mock(RoleResponse.class));

        assertNotNull(roleService.createRole(req));
        verify(permissionRepository, never()).findAllById(any());
    }

    @Test
    void testCreateRole_Exists() {
        RoleRequest req = new RoleRequest();
        req.setName("ADMIN");
        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(new RoleEntity()));

        assertThrows(ResourceConflictException.class, () -> roleService.createRole(req));
    }

    @Test
    void testUpdateRole_Success() {
        RoleRequest req = new RoleRequest();
        req.setName("NEW_ADMIN");
        req.setPermissionIds(java.util.List.of(1L));

        RoleEntity role = new RoleEntity();
        when(roleRepository.findById(1L)).thenReturn(Optional.of(role));
        when(permissionRepository.findAllById(java.util.List.of(1L))).thenReturn(List.of(new PermissionEntity()));
        when(roleRepository.save(role)).thenReturn(role);
        when(userMapper.toRoleResponse(role)).thenReturn(org.mockito.Mockito.mock(RoleResponse.class));

        assertNotNull(roleService.updateRole(1L, req));
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
        req.setPermissionIds(null);
        RoleEntity role = new RoleEntity();

        when(roleRepository.findById(1L)).thenReturn(Optional.of(role));
        when(roleRepository.save(role)).thenReturn(role);
        when(userMapper.toRoleResponse(role)).thenReturn(org.mockito.Mockito.mock(RoleResponse.class));

        assertNotNull(roleService.updateRole(1L, req));
        verify(permissionRepository, never()).findAllById(any());
    }

    @Test
    void testDeleteRole_Success() {
        RoleEntity role = new RoleEntity();
        when(roleRepository.findById(1L)).thenReturn(Optional.of(role));
        when(userRepository.existsByRole(role)).thenReturn(false);

        roleService.deleteRole(1L);

        verify(roleRepository).delete(role);
    }

    @Test
    void testDeleteRole_NotFound() {
        when(roleRepository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> roleService.deleteRole(1L));
    }

    @Test
    void testDeleteRole_InUse() {
        RoleEntity role = new RoleEntity();
        when(roleRepository.findById(1L)).thenReturn(Optional.of(role));
        when(userRepository.existsByRole(role)).thenReturn(true);

        assertThrows(ResourceConflictException.class, () -> roleService.deleteRole(1L));
        verify(roleRepository, never()).delete(any());
    }
}
