package com.web.backend.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import com.web.backend.model.postgres.PermissionEntity;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class PermissionRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PermissionRepository permissionRepository;

    @Test
    void testFindByName_Success() {
        PermissionEntity permission = new PermissionEntity();
        permission.setName("READ");
        entityManager.persistAndFlush(permission);

        Optional<PermissionEntity> found = permissionRepository.findByName("READ");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("READ");
    }

    @Test
    void testFindByName_NotFound() {
        Optional<PermissionEntity> found = permissionRepository.findByName("UNKNOWN");
        assertThat(found).isNotPresent();
    }
}
