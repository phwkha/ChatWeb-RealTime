package com.web.backend.mapper;

import com.web.backend.controller.request.*;
import com.web.backend.controller.response.*;
import com.web.backend.model.AddressEntity;
import com.web.backend.model.PermissionEntity;
import com.web.backend.model.RoleEntity;
import com.web.backend.model.UserEntity;
import org.mapstruct.*;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.Collections;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE, nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface UserMapper {

    @Mapping(target = "role", source = "role.name")
    @Mapping(target = "permissions", source = "entity", qualifiedByName = "mapPermissionsToStrings")
    UserResponse toUserResponse(UserEntity entity);

    @Named("mapPermissionsToStrings")
    default Set<String> mapPermissionsToStrings(UserEntity entity) {
        if (entity == null)
            return Collections.emptySet();
        return entity.getAuthorities().stream()
                .map(auth -> auth.getAuthority())
                .collect(Collectors.toSet());
    }

    AddressResponse toAddressResponse(AddressEntity entity);

    UserSummaryResponse toUserSummaryResponse(UserEntity entity);

    @Mapping(target = "role", source = "role.name")
    @Mapping(target = "permissions", source = "entity", qualifiedByName = "mapPermissionsToStrings")
    UserDetailResponse toUserDetailResponse(UserEntity entity);

    @Mapping(target = "userStatus", ignore = true)
    UserEntity toEntity(AdminCreateUserRequest request);

    AddressEntity toAddressEntity(AddressRequest request);

    @Mapping(target = "avatar", ignore = true)
    void updateUserFromRequest(UpdateUserRequest request, @MappingTarget UserEntity entity);

    void updateAdminUserFromRequest(AdminUpdateUserRequest request, @MappingTarget UserEntity entity);

    void updateAddressFromRequest(AddressRequest request, @MappingTarget AddressEntity entity);

    PermissionResponse toPermissionResponse(PermissionEntity entity);

    @Mapping(target = "permissions", source = "permissions")
    RoleResponse toRoleResponse(RoleEntity entity);

    void updateRoleFromRequest(RoleRequest request, @MappingTarget RoleEntity entity);
}