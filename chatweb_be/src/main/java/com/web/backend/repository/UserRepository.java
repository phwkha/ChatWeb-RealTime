package com.web.backend.repository;

import com.web.backend.common.UserStatus;
import com.web.backend.controller.response.UserSummaryResponse;
import com.web.backend.model.postgres.RoleEntity;
import com.web.backend.model.postgres.UserEntity;
import com.web.backend.repository.projection.UserRsaKeyProjection;

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

    Optional<UserEntity> findByUsername(String username);

    @EntityGraph(attributePaths = { "role", "role.permissions" })
    Optional<UserEntity> findWithAuthoritiesByUsername(String username);

    Optional<UserEntity> findByEmail(String email);

    @Query("SELECT u.email FROM UserEntity u WHERE u.username = :username")
    Optional<String> findEmailByUsername(@Param("username") String username);

    Optional<UserEntity> findByProviderId(String providerId);

    @Query("""
            SELECT new com.web.backend.controller.response.UserSummaryResponse(
                u.username, u.firstName, u.lastName, u.avatar, u.isOnline, u.userStatus
            )
            FROM UserEntity u
            WHERE u.username IN :usernames
            """)
    List<UserSummaryResponse> findSummaryByUsernameIn(@Param("usernames") Collection<String> usernames);

    @Query("SELECT u.userStatus FROM UserEntity u WHERE u.username = :username")
    Optional<UserStatus> findUserStatusByUsername(@Param("username") String username);

    @Query("SELECT u.publicKey FROM UserEntity u WHERE u.username = :username")
    Optional<String> findPublicKeyByUsername(@Param("username") String username);

    @Query("""
            SELECT new com.web.backend.repository.projection.UserRsaKeyProjection(u.userStatus, u.encryptedRsaPrivateKey)
            FROM UserEntity u
            WHERE u.username = :username
            """)
    Optional<UserRsaKeyProjection> findRsaKeyProjectionByUsername(@Param("username") String username);

    @Query("SELECT u.avatar FROM UserEntity u WHERE u.username = :username")
    Optional<String> findAvatarByUsername(@Param("username") String username);

    @Query(value = """
            SELECT new com.web.backend.controller.response.UserSummaryResponse(
                u.username, u.firstName, u.lastName, u.avatar, u.isOnline, u.userStatus
            )
            FROM UserEntity u
            WHERE u.userStatus != :status
            """, countQuery = "SELECT count(u) FROM UserEntity u WHERE u.userStatus != :status")
    Page<UserSummaryResponse> findSummaryByUserStatusNot(@Param("status") UserStatus status, Pageable pageable);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByRole(RoleEntity role);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE UserEntity u SET u.isOnline = :isOnline WHERE u.username = :username")
    void updateOnlineStatus(String username, boolean isOnline);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE UserEntity u SET u.publicKey = :publicKey WHERE u.username = :username")
    void updatePublicKey(@Param("username") String username, @Param("publicKey") String publicKey);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE UserEntity u SET u.encryptedRsaPrivateKey = :encryptedKey WHERE u.username = :username")
    void updateEncryptedRsaPrivateKey(@Param("username") String username, @Param("encryptedKey") String encryptedKey);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE UserEntity u SET u.avatar = :avatar WHERE u.username = :username")
    void updateAvatar(@Param("username") String username, @Param("avatar") String avatar);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE UserEntity u SET u.tokenVersion = COALESCE(u.tokenVersion, 0) + 1 WHERE u.username = :username")
    void incrementTokenVersion(@Param("username") String username);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE UserEntity u SET u.email = :email WHERE u.username = :username")
    int updateEmail(@Param("username") String username, @Param("email") String email);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE UserEntity u SET u.phone = :phone WHERE u.username = :username")
    int updatePhone(@Param("username") String username, @Param("phone") String phone);
}
