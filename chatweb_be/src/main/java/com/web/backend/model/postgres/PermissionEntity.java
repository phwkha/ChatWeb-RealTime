package com.web.backend.model.postgres;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "permissions",
        indexes = {
                @Index(name = "idx_permission_name", columnList = "name")
        })
public class PermissionEntity extends AbstractEntity<Long> {

    @Column(name = "name")
    private String name;

    @Column(name = "description")
    private String description;

    @ManyToMany(mappedBy = "permissions")
    @JsonIgnore
    private Set<RoleEntity> roles = new HashSet<>();
}
