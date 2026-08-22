package com.web.backend.repository;

import com.web.backend.common.FriendshipStatus;
import com.web.backend.model.postgres.FriendshipEntity;
import com.web.backend.model.postgres.UserEntity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.List;
import org.springframework.data.repository.query.Param;

public interface FriendshipRepository extends JpaRepository<FriendshipEntity, Long> {

        @Query("SELECT f FROM FriendshipEntity f WHERE " +
                        "(f.requester = :user1 AND f.addressee = :user2) OR " +
                        "(f.requester = :user2 AND f.addressee = :user1)")
        Optional<FriendshipEntity> findByUsers(UserEntity user1, UserEntity user2);

        @Query("SELECT f FROM FriendshipEntity f WHERE " +
                        "(f.requester.username = :u1 AND f.addressee.username = :u2) OR " +
                        "(f.requester.username = :u2 AND f.addressee.username = :u1)")
        Optional<FriendshipEntity> findByUsernames(@Param("u1") String u1, @Param("u2") String u2);

        @Query("SELECT COUNT(f) > 0 FROM FriendshipEntity f WHERE " +
                        "((f.requester.username = :u1 AND f.addressee.username = :u2) OR " +
                        "(f.requester.username = :u2 AND f.addressee.username = :u1)) " +
                        "AND f.status = 'ACCEPTED'")
        boolean existsFriendship(String u1, String u2);

        Page<FriendshipEntity> findByAddresseeAndStatus(UserEntity addressee, FriendshipStatus status,
                        Pageable pageable);

        boolean existsByRequesterAndAddresseeAndStatus(UserEntity requester, UserEntity addressee,
                        FriendshipStatus status);

        @Query("SELECT f FROM FriendshipEntity f WHERE " +
                        "(f.requester = :user OR f.addressee = :user) AND f.status = 'ACCEPTED'")
        Page<FriendshipEntity> findAllAcceptedFriendships(UserEntity user, Pageable pageable);

        Page<FriendshipEntity> findByRequesterAndStatus(UserEntity requester, FriendshipStatus status,
                        Pageable pageable);

        @Query("SELECT CASE WHEN f.requester.username = :username THEN f.addressee.username ELSE f.requester.username END "
                        +
                        "FROM FriendshipEntity f " +
                        "WHERE (f.requester.username = :username OR f.addressee.username = :username) " +
                        "AND f.status = 'ACCEPTED'")
        List<String> findAllFriendUsernamesByUsername(@Param("username") String username);
}