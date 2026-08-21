package com.web.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.web.backend.model.postgres.RoleEntity;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<RoleEntity, Long> {
    Optional<RoleEntity> findByName(String name);
}
