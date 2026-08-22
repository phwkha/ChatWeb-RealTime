package com.web.backend.repository;

import com.web.backend.common.UserStatus;
import com.web.backend.model.postgres.RoleEntity;
import com.web.backend.model.postgres.UserEntity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long>, JpaSpecificationExecutor<UserEntity> {

        @EntityGraph(attributePaths = { "role", "role.permissions" })
        Optional<UserEntity> findByUsername(String username);

        Optional<UserEntity> findByEmail(String email);

        Optional<UserEntity> findByProviderId(String providerId);

        List<UserEntity> findByUsernameIn(Collection<String> usernames);

        @EntityGraph(attributePaths = { "role", "role.permissions" })
        Page<UserEntity> findAllByUserStatusNot(UserStatus status, Pageable pageable);

        boolean existsByUsername(String username);

        boolean existsByEmail(String email);

        boolean existsByRole(RoleEntity role);

        @Modifying
        @Transactional
        @Query("UPDATE UserEntity u SET u.isOnline = :isOnline WHERE u.username = :username")
        void updateOnlineStatus(String username, boolean isOnline);

        @EntityGraph(attributePaths = { "role", "role.permissions" })
        @Query("SELECT u FROM UserEntity u WHERE u.userStatus != :status " +
                        "AND (:currentUsername IS NULL OR u.username != :currentUsername) " +
                        "AND (:currentUsername IS NULL OR u.id NOT IN (" +
                        "    SELECT f.addressee.id FROM FriendshipEntity f WHERE f.requester.username = :currentUsername AND f.status = 'BLOCKED'" +
                        ")) " +
                        "AND (:currentUsername IS NULL OR u.id NOT IN (" +
                        "    SELECT f.requester.id FROM FriendshipEntity f WHERE f.addressee.username = :currentUsername AND f.status = 'BLOCKED'" +
                        ")) " +
                        "AND (LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
                        "     LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
                        "     LOWER(u.firstName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
                        "     LOWER(u.lastName) LIKE LOWER(CONCAT('%', :keyword, '%')))")
        Page<UserEntity> searchUsersByKeyword(
                        @Param("currentUsername") String currentUsername,
                        @Param("keyword") String keyword,
                        @Param("status") UserStatus status,
                        Pageable pageable);
}
