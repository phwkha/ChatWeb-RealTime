package com.web.backend.repository;

import com.web.backend.common.FriendshipStatus;
import com.web.backend.model.postgres.FriendshipEntity;
import com.web.backend.model.postgres.RoleEntity;
import com.web.backend.model.postgres.UserEntity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class FriendshipRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private FriendshipRepository friendshipRepository;

    private UserEntity user1;
    private UserEntity user2;

    @BeforeEach
    void setUp() {
        RoleEntity role = new RoleEntity();
        role.setName("USER");
        entityManager.persistAndFlush(role);

        user1 = new UserEntity();
        user1.setUsername("user1");
        user1.setEmail("user1@example.com");
        user1.setPassword("pwd");
        user1.setRole(role);
        entityManager.persistAndFlush(user1);

        user2 = new UserEntity();
        user2.setUsername("user2");
        user2.setEmail("user2@example.com");
        user2.setPassword("pwd");
        user2.setRole(role);
        entityManager.persistAndFlush(user2);
    }

    @Test
    void testFindByUsers_Success() {
        FriendshipEntity friendship = new FriendshipEntity();
        friendship.setRequester(user1);
        friendship.setAddressee(user2);
        friendship.setStatus(FriendshipStatus.ACCEPTED);
        entityManager.persistAndFlush(friendship);

        Optional<FriendshipEntity> found = friendshipRepository.findByUsers(user1, user2);
        assertThat(found).isPresent();
        assertThat(found.get().getRequester().getUsername()).isEqualTo("user1");

        Optional<FriendshipEntity> foundReverse = friendshipRepository.findByUsers(user2, user1);
        assertThat(foundReverse).isPresent();
    }

    @Test
    void testExistsFriendship_Success() {
        FriendshipEntity friendship = new FriendshipEntity();
        friendship.setRequester(user1);
        friendship.setAddressee(user2);
        friendship.setStatus(FriendshipStatus.ACCEPTED);
        entityManager.persistAndFlush(friendship);

        boolean exists = friendshipRepository.existsFriendship("user1", "user2");
        assertThat(exists).isTrue();

        boolean existsReverse = friendshipRepository.existsFriendship("user2", "user1");
        assertThat(existsReverse).isTrue();

        boolean notExists = friendshipRepository.existsFriendship("user1", "unknown");
        assertThat(notExists).isFalse();
    }

    @Test
    void testFindByAddresseeAndStatus_Success() {
        FriendshipEntity friendship = new FriendshipEntity();
        friendship.setRequester(user1);
        friendship.setAddressee(user2);
        friendship.setStatus(FriendshipStatus.PENDING);
        entityManager.persistAndFlush(friendship);

        Page<FriendshipEntity> page = friendshipRepository.findByAddresseeAndStatus(user2, FriendshipStatus.PENDING,
                PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getRequester().getUsername()).isEqualTo("user1");
    }

    @Test
    void testExistsByRequesterAndAddresseeAndStatus_Success() {
        FriendshipEntity friendship = new FriendshipEntity();
        friendship.setRequester(user1);
        friendship.setAddressee(user2);
        friendship.setStatus(FriendshipStatus.PENDING);
        entityManager.persistAndFlush(friendship);

        boolean exists = friendshipRepository.existsByRequesterAndAddresseeAndStatus(user1, user2,
                FriendshipStatus.PENDING);
        assertThat(exists).isTrue();
    }

    @Test
    void testFindAllAcceptedFriendships_Success() {
        FriendshipEntity friendship = new FriendshipEntity();
        friendship.setRequester(user1);
        friendship.setAddressee(user2);
        friendship.setStatus(FriendshipStatus.ACCEPTED);
        entityManager.persistAndFlush(friendship);

        Page<FriendshipEntity> page = friendshipRepository.findAllAcceptedFriendships(user1, PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isEqualTo(1);
    }
}
