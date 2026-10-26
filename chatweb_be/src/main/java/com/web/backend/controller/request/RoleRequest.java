package com.web.backend.controller.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.List;

@Data
public class RoleRequest {
    @NotBlank(message = "{valid.role_empty}")
    private String name;

    private String description;

    private Boolean userRole;

    private List<Long> permissionIds;
}