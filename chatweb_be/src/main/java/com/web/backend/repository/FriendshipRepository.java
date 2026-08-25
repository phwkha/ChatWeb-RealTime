package com.web.backend.repository;

import com.web.backend.common.FriendshipStatus;
import com.web.backend.controller.response.UserSummaryResponse;
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

    @Query(value = """
            SELECT new com.web.backend.controller.response.UserSummaryResponse(
                f.requester.username, f.requester.firstName, f.requester.lastName,
                f.requester.avatar, f.requester.isOnline, f.requester.userStatus
            )
            FROM FriendshipEntity f
            WHERE f.addressee = :addressee AND f.status = :status
            """, countQuery = "SELECT count(f) FROM FriendshipEntity f WHERE f.addressee = :addressee AND f.status = :status")
    Page<UserSummaryResponse> findRequesterSummaryByAddresseeAndStatus(
            @Param("addressee") UserEntity addressee,
            @Param("status") FriendshipStatus status,
            Pageable pageable);

    @Query(value = """
            SELECT new com.web.backend.controller.response.UserSummaryResponse(
                f.addressee.username, f.addressee.firstName, f.addressee.lastName,
                f.addressee.avatar, f.addressee.isOnline, f.addressee.userStatus
            )
            FROM FriendshipEntity f
            WHERE f.requester = :requester AND f.status = :status
            """, countQuery = "SELECT count(f) FROM FriendshipEntity f WHERE f.requester = :requester AND f.status = :status")
    Page<UserSummaryResponse> findAddresseeSummaryByRequesterAndStatus(
            @Param("requester") UserEntity requester,
            @Param("status") FriendshipStatus status,
            Pageable pageable);

    @Query("""
            SELECT new com.web.backend.controller.response.UserSummaryResponse(
                u.username, u.firstName, u.lastName, u.avatar, u.isOnline, u.userStatus
            )
            FROM UserEntity u
            WHERE EXISTS (
                SELECT 1 FROM FriendshipEntity f
                WHERE ((f.requester.username = :username AND f.addressee = u)
                    OR (f.addressee.username = :username AND f.requester = u))
                  AND f.status = 'ACCEPTED'
            )
            """)
    Page<UserSummaryResponse> findFriendsSummaryByUsername(@Param("username") String username, Pageable pageable);

    @Query("SELECT CASE WHEN f.requester.username = :username THEN f.addressee.username ELSE f.requester.username END "
            +
            "FROM FriendshipEntity f " +
            "WHERE (f.requester.username = :username OR f.addressee.username = :username) " +
            "AND f.status = 'ACCEPTED'")
    List<String> findAllFriendUsernamesByUsername(@Param("username") String username);
}