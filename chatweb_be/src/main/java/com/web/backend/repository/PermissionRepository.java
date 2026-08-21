package com.web.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.web.backend.model.postgres.PermissionEntity;

import java.util.Optional;

public interface PermissionRepository extends JpaRepository<PermissionEntity, Long> {
    Optional<PermissionEntity> findByName(String name);
}
