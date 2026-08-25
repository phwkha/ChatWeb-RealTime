package com.web.backend.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;

import com.web.backend.model.postgres.RoleEntity;

import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<RoleEntity, Long> {
    Optional<RoleEntity> findByName(String name);

    @Override
    @NonNull
    @EntityGraph(attributePaths = { "permissions" })
    List<RoleEntity> findAll();
}
