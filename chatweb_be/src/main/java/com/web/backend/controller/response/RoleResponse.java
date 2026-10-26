package com.web.backend.controller.response;

import lombok.Builder;
import lombok.Data;
import java.util.Set;

@Data
@Builder
public class RoleResponse {
    private Long id;
    private String name;
    private String description;
    private boolean userRole;
    private Set<PermissionResponse> permissions;
}