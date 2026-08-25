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

    @Test
    void testSearchUsersSummaryWithSpecifications_Success() {
        RoleEntity role = new RoleEntity();
        role.setName("USER");
        entityManager.persistAndFlush(role);

        UserEntity user = new UserEntity();
        user.setUsername("john_doe");
        user.setEmail("john@example.com");
        user.setFirstName("John");
        user.setLastName("Doe");
        user.setPassword("password");
        user.setUserStatus(com.web.backend.common.UserStatus.ACTIVE);
        user.setRole(role);
        entityManager.persistAndFlush(user);

        org.springframework.data.jpa.domain.Specification<UserEntity> spec =
                com.web.backend.repository.specification.UserSearchSpecifications.isNotStatus(com.web.backend.common.UserStatus.INACTIVE)
                        .and(com.web.backend.repository.specification.UserSearchSpecifications.containsKeyword("john"));

        org.springframework.data.domain.Page<UserEntity> result =
                userRepository.findAll(spec, org.springframework.data.domain.PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getUsername()).isEqualTo("john_doe");
        assertThat(result.getContent().get(0).getFirstName()).isEqualTo("John");
    }

    @Test
    void testFindSummaryByUserStatusNot_Success() {
        RoleEntity role = new RoleEntity();
        role.setName("USER");
        entityManager.persistAndFlush(role);

        UserEntity user = new UserEntity();
        user.setUsername("active_user");
        user.setEmail("active@example.com");
        user.setPassword("password");
        user.setUserStatus(com.web.backend.common.UserStatus.ACTIVE);
        user.setRole(role);
        entityManager.persistAndFlush(user);

        org.springframework.data.domain.Page<com.web.backend.controller.response.UserSummaryResponse> result =
                userRepository.findSummaryByUserStatusNot(com.web.backend.common.UserStatus.INACTIVE, org.springframework.data.domain.PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void testFindSummaryByUsernameIn_Success() {
        RoleEntity role = new RoleEntity();
        role.setName("USER");
        entityManager.persistAndFlush(role);

        UserEntity user = new UserEntity();
        user.setUsername("online_user");
        user.setEmail("online@example.com");
        user.setPassword("password");
        user.setUserStatus(com.web.backend.common.UserStatus.ACTIVE);
        user.setRole(role);
        entityManager.persistAndFlush(user);

        java.util.List<com.web.backend.controller.response.UserSummaryResponse> list =
                userRepository.findSummaryByUsernameIn(java.util.List.of("online_user"));

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getUsername()).isEqualTo("online_user");
    }

    @Test
    void testSingleFieldOperations_Success() {
        RoleEntity role = new RoleEntity();
        role.setName("USER");
        entityManager.persistAndFlush(role);

        UserEntity user = new UserEntity();
        user.setUsername("field_user");
        user.setEmail("field@example.com");
        user.setPassword("password");
        user.setUserStatus(com.web.backend.common.UserStatus.ACTIVE);
        user.setPublicKey("initial_pub");
        user.setEncryptedRsaPrivateKey("initial_rsa");
        user.setAvatar("initial_avatar.jpg");
        user.setTokenVersion(1);
        user.setRole(role);
        entityManager.persistAndFlush(user);

        // 1. findPublicKeyByUsername
        Optional<String> pubKey = userRepository.findPublicKeyByUsername("field_user");
        assertThat(pubKey).contains("initial_pub");

        // 2. findRsaKeyProjectionByUsername
        var rsaProj = userRepository.findRsaKeyProjectionByUsername("field_user");
        assertThat(rsaProj).isPresent();
        assertThat(rsaProj.get().encryptedRsaPrivateKey()).isEqualTo("initial_rsa");
        assertThat(rsaProj.get().userStatus()).isEqualTo(com.web.backend.common.UserStatus.ACTIVE);

        // 3. findAvatarByUsername
        Optional<String> avatar = userRepository.findAvatarByUsername("field_user");
        assertThat(avatar).contains("initial_avatar.jpg");

        // 4. updatePublicKey
        userRepository.updatePublicKey("field_user", "updated_pub");
        entityManager.clear();
        assertThat(userRepository.findPublicKeyByUsername("field_user")).contains("updated_pub");

        // 5. updateEncryptedRsaPrivateKey
        userRepository.updateEncryptedRsaPrivateKey("field_user", "updated_rsa");
        entityManager.clear();
        assertThat(userRepository.findRsaKeyProjectionByUsername("field_user").get().encryptedRsaPrivateKey()).isEqualTo("updated_rsa");

        // 6. updateAvatar
        userRepository.updateAvatar("field_user", "updated_avatar.jpg");
        entityManager.clear();
        assertThat(userRepository.findAvatarByUsername("field_user")).contains("updated_avatar.jpg");

        // 7. incrementTokenVersion
        userRepository.incrementTokenVersion("field_user");
        entityManager.clear();
        Optional<UserEntity> reloaded = userRepository.findByUsername("field_user");
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getTokenVersion()).isEqualTo(2);

        // 8. findEmailByUsername
        Optional<String> email = userRepository.findEmailByUsername("field_user");
        assertThat(email).contains("field@example.com");

        // 9. updateEmail
        userRepository.updateEmail("field_user", "updated_email@example.com");
        entityManager.clear();
        assertThat(userRepository.findEmailByUsername("field_user")).contains("updated_email@example.com");

        // 10. updatePhone
        userRepository.updatePhone("field_user", "0987654321");
        entityManager.clear();
        assertThat(userRepository.findByUsername("field_user").get().getPhone()).isEqualTo("0987654321");
    }
}
