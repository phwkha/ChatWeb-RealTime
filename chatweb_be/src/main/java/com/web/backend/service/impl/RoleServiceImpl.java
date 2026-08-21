package com.web.backend.service.impl;

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
import com.web.backend.service.RoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j(topic = "ROLE-SERVICE")
public class RoleServiceImpl implements RoleService {

    private final RoleRepository roleRepository;

    private final PermissionRepository permissionRepository;

    private final UserMapper userMapper;

    private final UserRepository userRepository;

    private static final String USER_DETAILS_STRING = "user_details";

    private static final String ERROR_ROLE_EXISTS_STRING = "error.role.exists";
    private static final String ERROR_ROLE_NOT_FOUND_STRING = "error.role.not_found";
    private static final String ERROR_ROLE_IN_USE_STRING = "error.role.in_use";

    @Override
    public List<RoleResponse> getAllRoles() {
        return roleRepository.findAll().stream()
                .map(userMapper::toRoleResponse)
                .toList();
    }

    @Override
    public List<PermissionResponse> getAllPermissions() {
        return permissionRepository.findAll().stream()
                .map(userMapper::toPermissionResponse)
                .toList();
    }

    @Override
    @Transactional
    public RoleResponse createRole(RoleRequest request) {
        if (roleRepository.findByName(request.getName()).isPresent()) {
            throw new ResourceConflictException(Translator.tolocale(ERROR_ROLE_EXISTS_STRING, request.getName()));
        }

        RoleEntity role = new RoleEntity();
        role.setName(request.getName());
        role.setDescription(request.getDescription());

        if (request.getPermissionIds() != null && !request.getPermissionIds().isEmpty()) {
            Set<PermissionEntity> permissions = new HashSet<>(
                    permissionRepository.findAllById(Objects.requireNonNull(request.getPermissionIds())));
            role.setPermissions(permissions);
        }

        RoleEntity savedRole = roleRepository.save(role);
        log.info("Created new role '{}'", savedRole.getName());
        return userMapper.toRoleResponse(savedRole);
    }

    @Override
    @Transactional
    @CacheEvict(value = USER_DETAILS_STRING, allEntries = true)
    public RoleResponse updateRole(@NonNull Long roleId, RoleRequest request) {
        RoleEntity role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException(Translator.tolocale(ERROR_ROLE_NOT_FOUND_STRING)));

        role.setName(request.getName());
        role.setDescription(request.getDescription());

        if (request.getPermissionIds() != null) {
            Set<PermissionEntity> permissions = new HashSet<>(
                    permissionRepository.findAllById(Objects.requireNonNull(request.getPermissionIds())));
            role.setPermissions(permissions);
        }

        RoleEntity savedRole = roleRepository.save(role);
        log.info("Updated role '{}' (id={}) and evicted user details cache", savedRole.getName(), roleId);
        return userMapper.toRoleResponse(savedRole);
    }

    @Override
    @Transactional
    @CacheEvict(value = USER_DETAILS_STRING, allEntries = true)
    public void deleteRole(@NonNull Long roleId) {
        RoleEntity role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException(Translator.tolocale(ERROR_ROLE_NOT_FOUND_STRING)));

        if (userRepository.existsByRole(role)) {
            throw new ResourceConflictException(Translator.tolocale(ERROR_ROLE_IN_USE_STRING));
        }
        roleRepository.delete(Objects.requireNonNull(role));
        log.info("Deleted role id={} and evicted user details cache", roleId);
    }
}