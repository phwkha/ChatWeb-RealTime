package com.web.backend.repository;

import com.web.backend.model.postgres.RoleEntity;
import com.web.backend.model.postgres.UserEntity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Test
    void testFindByUsername_Success() {
        RoleEntity role = new RoleEntity();
        role.setName("USER");
        entityManager.persistAndFlush(role);

        // Arrange: Setup mock data in the in-memory DB
        UserEntity user = new UserEntity();
        user.setUsername("testrepo");
        user.setEmail("testrepo@example.com");
        user.setPassword("password");
        user.setRole(role);
        entityManager.persistAndFlush(user);

        // Act: Call the repository method
        Optional<UserEntity> found = userRepository.findByUsername("testrepo");

        // Assert: Verify the result
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("testrepo@example.com");
    }

    @Test
    void testExistsByEmail_Success() {
        RoleEntity role = new RoleEntity();
        role.setName("USER");
        entityManager.persistAndFlush(role);

        UserEntity user = new UserEntity();
        user.setUsername("testrepo2");
        user.setEmail("testrepo2@example.com");
        user.setPassword("password");
        user.setRole(role);
        entityManager.persistAndFlush(user);

        boolean exists = userRepository.existsByEmail("testrepo2@example.com");
        boolean notExists = userRepository.existsByEmail("unknown@example.com");

        assertThat(exists).isTrue();
        assertThat(notExists).isFalse();
    }
}
