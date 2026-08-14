package com.web.backend.controller;

import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.controller.request.RoleRequest;
import com.web.backend.controller.response.ApiResponse;
import com.web.backend.controller.response.PermissionResponse;
import com.web.backend.controller.response.RoleResponse;
import com.web.backend.model.UserEntity;
import com.web.backend.service.RoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Role Controller")
@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
@Slf4j(topic = "ROLE-CONTROLLER")
public class RoleController {

    private final RoleService roleService;

    private static final String SUCCESS_ROLE_GET_LIST_STRING = "success.role.get_list";
    private static final String SUCCESS_ROLE_GET_PERMISSIONS_STRING = "success.role.get_permissions";
    private static final String SUCCESS_ROLE_CREATE_STRING = "success.role.create";
    private static final String SUCCESS_ROLE_UPDATE_STRING = "success.role.update";
    private static final String SUCCESS_ROLE_DELETE_STRING = "success.role.delete";

    @Operation(summary = "Get all roles", description = "API endpoint for get all roles")
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_VIEW_ALL')")
    public ResponseEntity<ApiResponse<List<RoleResponse>>> getAllRoles() {
        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK.value(),
                Translator.tolocale(SUCCESS_ROLE_GET_LIST_STRING),
                roleService.getAllRoles()));
    }

    @Operation(summary = "Get all permissions", description = "API endpoint for get all permissions")
    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('ROLE_VIEW_ALL_PERMISSION')")
    public ResponseEntity<ApiResponse<List<PermissionResponse>>> getAllPermissions() {
        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK.value(),
                Translator.tolocale(SUCCESS_ROLE_GET_PERMISSIONS_STRING),
                roleService.getAllPermissions()));
    }

    @Operation(summary = "Create role", description = "API endpoint for create role")
    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADD')")
    public ResponseEntity<ApiResponse<RoleResponse>> createRole(@RequestBody @Valid RoleRequest request,
            Authentication authentication) {
        UserEntity userEntityPrincipal = (UserEntity) authentication.getPrincipal();
        log.debug("User '{}' creating new role '{}'", userEntityPrincipal.getUsername(), request.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                HttpStatus.CREATED.value(),
                Translator.tolocale(SUCCESS_ROLE_CREATE_STRING),
                roleService.createRole(request)));
    }

    @Operation(summary = "Update role", description = "API endpoint for update role")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_UPDATE')")
    public ResponseEntity<ApiResponse<RoleResponse>> updateRole(@PathVariable @NonNull Long id,
            @RequestBody @Valid RoleRequest request, Authentication authentication) {
        UserEntity userEntityPrincipal = (UserEntity) authentication.getPrincipal();
        log.debug("User '{}' updating role id={}", userEntityPrincipal.getUsername(), id);
        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK.value(),
                Translator.tolocale(SUCCESS_ROLE_UPDATE_STRING),
                roleService.updateRole(id, request)));
    }

    @Operation(summary = "Delete role", description = "API endpoint for delete role")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_DELETE')")
    public ResponseEntity<ApiResponse<Void>> deleteRole(@PathVariable @NonNull Long id, Authentication authentication) {
        UserEntity userEntityPrincipal = (UserEntity) authentication.getPrincipal();
        log.debug("User '{}' deleting role id={}", userEntityPrincipal.getUsername(), id);
        roleService.deleteRole(id);
        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.NO_CONTENT.value(),
                Translator.tolocale(SUCCESS_ROLE_DELETE_STRING),
                null));
    }
}